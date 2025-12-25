(ns plin.boot
  "A standard bootstrapper for Plin applications.
   
   Supports:
   - nbb (Node.js Babashka)
   - scittle (Browser interpretation)
   - cljs (Compiled ClojureScript)
   - clj (JVM Clojure)"
  (:require 
   [plin.core :as pi]
   [promesa.core :as p]
   ;; Atom implementation selection:
   ;; - nbb/clj: Standard atom
   ;; - scittle/cljs: Reagent atom (for UI reactivity)
   #?(:nbb     [cljs.core :as r]
      :scittle [reagent.core :as r]
      :clj     [clojure.core :as r]
      :cljs    [reagent.core :as r])))

;; --- Platform Helpers ---

(defn- log [& args]
  #?(:clj (apply println args)
     :default (apply js/console.log args)))

(defn- log-error [e]
  #?(:clj (println "ERROR:" e)
     :default (js/console.error e)))

;; --- State ---

(defonce ^:private state 
  ;; Reagent atom (Browser) or Standard atom (Server) holding the system state.
  ;; PRIVATE: Do not access this directly from other namespaces.
  ;; Use the ::api bean if you need access to the system state.
  (r/atom {:all-plugins []
           :disabled-ids #{}
           :container nil
           :last-error nil}))

;; --- Logic ---

(defn get-cascading-disabled
  "Calculates the set of effectively disabled plugins based on dependencies."
  [plugins disabled-ids]
  (loop [current-disabled disabled-ids]
    (let [newly-disabled 
          (reduce
           (fn [acc plugin]
             (if (and (not (contains? current-disabled (:id plugin)))
                      (some #(contains? current-disabled %) (:deps plugin)))
               (conj acc (:id plugin))
               acc))
           #{}
           plugins)]
      (if (empty? newly-disabled)
        current-disabled
        (recur (into current-disabled newly-disabled))))))

(declare reload!)

(defn- update-plugin-list [plugins new-plugin]
  (let [target-id (:id new-plugin)
        ;; Find index of existing plugin with same ID
        idx (reduce-kv (fn [_ i p] 
                         (if (= (:id p) target-id) (reduced i) nil)) 
                       nil 
                       plugins)]
    (if idx
      ;; Replace existing plugin to preserve order/dependencies
      (assoc plugins idx new-plugin)
      ;; Append new plugin
      (conj plugins new-plugin))))

(defn register-plugin!
  "Registers a new plugin definition (or updates an existing one) and reloads the system.
   Returns a Promise."
  [plugin-def]
  (swap! state update :all-plugins update-plugin-list plugin-def)
  (reload!))

;; --- Plugin Definition ---

(def plugin
  "The Bootstrapper Plugin."
  (pi/plugin
   {:doc "System Bootstrapper. Manages the plugin lifecycle and system state."
    :deps []
    
    :beans
    {::api
     ^{:doc "System Control API.
             This is a function that returns a map: `{:state <atom> :reload! <fn> :register-plugin! <fn>}`."
       :api {:ret :map}}
     [(fn [] {:state state
              :reload! reload!
              :register-plugin! register-plugin!})]}}))

;; --- Reload Implementation ---

(defn reload!
  "Reloads the system based on the current state.

   1.  Calculates the active plugins (filtering out disabled ones).
   2.  Calls `plin.core/load-plugins` to create a new container.
   3.  Looks for a mount function in the container (specifically `:plinpt.p-app-shell/mount`).
   4.  Updates the `state` atom with the new container.
   5.  Executes the mount function to render the app.

   Returns a Promise that resolves to the state."
  []
  ;; Use p/delay to yield execution (equivalent to setTimeout 0)
  ;; This allows UI to render in browser or event loop to tick in Node
  (-> (p/delay 0)
      (p/then (fn [_]
                (log "System: Reloading...")
                (try
                  (let [{:keys [all-plugins disabled-ids]} @state

                        ;; Calculate which plugins to actually load
                        final-disabled (get-cascading-disabled all-plugins disabled-ids)
                        plugins-to-load (filter #(not (contains? final-disabled (:id %))) all-plugins)

                        ;; Create Container
                        container (pi/load-plugins (vec plugins-to-load))

                        ;; Resolve the mount function.
                        mount-fn (:plinpt.p-app-shell/mount container)]

                    ;; Update State
                    (swap! state assoc :container container :last-error nil)

                    ;; Mount App (if applicable in this environment)
                    (when mount-fn (mount-fn))

                    (log "System: Reload complete. Active plugins:" (count plugins-to-load))
                    @state)
                  (catch #?(:clj Exception :default :default) e
                    (log-error e)
                    (swap! state assoc :last-error e)
                    (throw e)))))))

;; --- Bootstrap ---

(defn bootstrap! 
  [plugins]
  (let [full-list (conj (vec plugins) plugin)]
    (swap! state assoc :all-plugins full-list)
    (reload!)))
