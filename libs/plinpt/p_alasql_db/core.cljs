(ns plinpt.p-alasql-db.core
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; Detect environment and load alasql
(def is-node?
  (not (nil? (try js/process (catch :default _ nil)))))

(def alasql 
  (if is-node?
    (try (js/require "alasql") (catch :default _ nil))
    (try (.-alasql js/window) (catch :default _ nil))))

(defonce state (atom {:defs []}))

(defn- run-sql [sql & [params]]
  (when alasql
    (alasql sql (clj->js params))))

(defn- exec-def [def]
  (doseq [s (:schema def)] (run-sql s))
  (doseq [d (:data def)] (run-sql d)))

(defn- sort-defs [defs]
  (let [id-map (into {} (map (juxt :id identity) defs))
        sorted (atom [])
        visited (atom #{})
        visiting (atom #{})]
    (defn visit [id]
      (when (contains? @visiting id)
        (throw (ex-info "Cyclic dependency in DB init" {:id id})))
      (when-not (contains? @visited id)
        (swap! visiting conj id)
        (let [def (get id-map id)]
          (when def
            (doseq [dep (:deps def)]
              (visit dep))
            (swap! sorted conj def)))
        (swap! visiting disj id)
        (swap! visited conj id)))
    
    (doseq [def defs]
      (visit (:id def)))
    @sorted))

(defn init-db! [defs]
  (reset! state {:defs defs})
  (js/console.log "Initializing AlaSQL DB..." (count defs) "modules")
  
  (if is-node?
    ;; Server (Node): Use In-Memory Database
    (do
      (run-sql "CREATE DATABASE IF NOT EXISTS pluggable_demo_db")
      (run-sql "USE pluggable_demo_db"))
    
    ;; Browser: Use LocalStorage for persistence across reloads
    (do
      (run-sql "CREATE LOCALSTORAGE DATABASE IF NOT EXISTS pluggable_demo_db")
      (run-sql "ATTACH LOCALSTORAGE DATABASE pluggable_demo_db")
      (run-sql "USE pluggable_demo_db")))
  
  (let [sorted (sort-defs defs)]
    (doseq [def sorted]
      (exec-def def)))
  
  (js/console.log "DB Init Complete."))

(defn reset-db! []
  (js/console.log "Resetting AlaSQL DB...")
  (run-sql "USE pluggable_demo_db")
  (let [tables (js->clj (run-sql "SHOW TABLES") :keywordize-keys true)]
    (doseq [t tables]
      (run-sql (str "DROP TABLE IF EXISTS " (:tableid t)))))
  
  (init-db! (:defs @state)))

(defn- lowercase-keys [m]
  (into {} (map (fn [[k v]] [(keyword (str/lower-case (name k))) v]) m)))

(defn executor [sql params]
  (js/Promise.
   (fn [resolve reject]
     (try
       (let [res (if params
                   (alasql sql (clj->js params))
                   (alasql sql))
             clj-res (js->clj res :keywordize-keys true)
             ;; Ensure all keys are lowercase to match Clojure expectations
             normalized-res (if (sequential? clj-res)
                              (map lowercase-keys clj-res)
                              clj-res)]
         (resolve normalized-res))
       (catch :default e
         (js/console.error "SQL Error:" e)
         (reject e))))))

(defn process-init [db values]
  ;; values is a list of contributions from plugins.
  ;; We flatten it to get a single list of maps.
  (let [defs (vec (flatten values))]
    ;; Initialize immediately upon loading
    (init-db! defs)
    ;; Store in DB for reference if needed, though state atom holds it for reset
    (assoc db :p-alasql-db/defs defs)))
