(ns plinpt.p-alasql-db.core
  (:require [clojure.set :as set]
            [clojure.string :as str]))

;; Detect environment and load alasql
(def is-node?
  (not (nil? (try js/process (catch :default _ nil)))))

(defn get-alasql
  "Lazily retrieves the alasql instance. This ensures we get it at runtime
   rather than at namespace load time, which is important for standalone builds
   where script evaluation order may differ."
  []
  (if is-node?
    (try (js/require "alasql") (catch :default _ nil))
    (try (.-alasql js/window) (catch :default _ nil))))

;; Keep for backwards compatibility, but prefer get-alasql for runtime access
(def alasql (get-alasql))

(defonce state (atom {:defs []}))

(defn- run-sql [sql & [params]]
  (when-let [db (get-alasql)]
    (db sql (clj->js params))))

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

(defn- try-init-browser-db! []
  "Attempts to initialize browser localStorage database. 
   Returns true on success, false if there's a corruption issue."
  (try
    (run-sql "CREATE LOCALSTORAGE DATABASE IF NOT EXISTS pluggable_demo_db")
    (run-sql "ATTACH LOCALSTORAGE DATABASE pluggable_demo_db")
    (run-sql "USE pluggable_demo_db")
    true
    (catch :default e
      (js/console.warn "DB initialization failed, will try recovery:" (.-message e))
      false)))

(defn- clear-corrupted-db! []
  "Clears corrupted database entries from localStorage."
  (js/console.log "Attempting to clear corrupted database from localStorage...")
  (try
    ;; Clear all alasql-related localStorage keys
    (let [keys-to-remove (atom [])]
      (doseq [i (range (.-length js/localStorage))]
        (let [key (.key js/localStorage i)]
          (when (and key (or (str/starts-with? key "alasql")
                             (str/starts-with? key "pluggable_demo_db")))
            (swap! keys-to-remove conj key))))
      (doseq [key @keys-to-remove]
        (js/console.log "  Removing localStorage key:" key)
        (.removeItem js/localStorage key)))
    (catch :default e
      (js/console.error "Error clearing localStorage:" e))))

(defn init-db! [defs]
  (reset! state {:defs defs})
  (let [db (get-alasql)]
    (js/console.log "Initializing AlaSQL DB..." (count defs) "modules" "| alasql available:" (boolean db))
    
    (when-not db
      (js/console.error "AlaSQL not found! window.alasql =" (.-alasql js/window))
      (throw (js/Error. "AlaSQL library not loaded")))
    
    (if is-node?
      ;; Server (Node): Use In-Memory Database
      (do
        (run-sql "CREATE DATABASE IF NOT EXISTS pluggable_demo_db")
        (run-sql "USE pluggable_demo_db"))
      
      ;; Browser: Use LocalStorage for persistence across reloads
      ;; Try normal init first, then recovery if needed
      (when-not (try-init-browser-db!)
        (clear-corrupted-db!)
        (when-not (try-init-browser-db!)
          (js/console.error "DB initialization failed even after recovery!")
          (throw (js/Error. "Cannot initialize AlaSQL database")))))
    
    (let [sorted (sort-defs defs)]
      (doseq [def sorted]
        (exec-def def)))
    
    (js/console.log "DB Init Complete.")))

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
       (let [db (get-alasql)
             _ (when-not db
                 (throw (js/Error. "AlaSQL not available - window.alasql is undefined")))
             _ (js/console.log "Executing SQL:" sql "with params:" (clj->js params))
             res (if params
                   (db sql (clj->js params))
                   (db sql))
             clj-res (js->clj res :keywordize-keys true)
             ;; Ensure all keys are lowercase to match Clojure expectations
             normalized-res (if (sequential? clj-res)
                              (map lowercase-keys clj-res)
                              clj-res)]
         (resolve normalized-res))
       (catch :default e
         (js/console.error "SQL Error:" (.-message e) "| SQL:" sql "| params:" (clj->js params))
         (js/console.error "Full error:" e)
         (reject e))))))

(defn process-init [db values]
  ;; values is a list of contributions from plugins.
  ;; We flatten it to get a single list of maps.
  (let [defs (vec (flatten values))]
    ;; Initialize immediately upon loading
    (init-db! defs)
    ;; Store in DB for reference if needed, though state atom holds it for reset
    (assoc db :p-alasql-db/defs defs)))
