(ns plin-platform.client-boot
  "Client-side bootstrap entry point.
   
   Reads the manifest from window.SYSTEM_MANIFEST (set by index.html),
   resolves plugins, and calls boot/bootstrap!"
  (:require [plin.boot :as boot]
            [plin.js-loader :as js-loader]
            [clojure.string :as str]))

;; --- Environment Detection ---

(def is-browser? 
  (try
    (some? js/window)
    (catch :default _ false)))

;; Get current mode and env from window (set by index.html)
(def current-mode 
  (if is-browser?
    (or (.-APP_MODE js/window) "demo")
    "demo"))

(def current-env
  (if is-browser?
    (or (.-APP_ENV js/window) "browser")
    "browser"))

;; --- Manifest Access ---
;; NOTE: We intentionally do NOT use :keywordize-keys to avoid scittle
;; trying to resolve namespaced keywords like :showcase.hello-react

(defn get-manifest []
  (if is-browser?
    (js->clj (.-SYSTEM_MANIFEST js/window))
    []))

(defn get-initially-disabled-ids []
  (if is-browser?
    (let [raw-ids (.-INITIALLY_DISABLED_IDS js/window)
          ids (js->clj raw-ids)
          result (set (map keyword ids))]
      result)
    #{}))

;; --- Manifest Entry Accessors ---
;; Helper functions to access manifest entries with string keys

(defn- get-entry-id [item]
  (get item "id"))

(defn- get-entry-type [item]
  (get item "type"))

(defn- get-entry-envs [item]
  (get item "envs"))

(defn- get-entry-modes [item]
  (get item "modes"))

(defn- get-entry-config [item]
  (get item "config"))

(defn- get-entry-files [item]
  (get item "files"))

(defn- get-entry-entry [item]
  (get item "entry"))

(defn- get-entry-enabled [item]
  (get item "enabled"))

;; --- Plugin Filtering ---

(defn should-load-plugin?
  "Check if a plugin should be loaded based on current env and mode.
   Load if: (envs absent OR current-env in envs) AND (modes absent OR current-mode in modes)"
  [item env mode]
  (and
   ;; Not a config entry
   (not (get-entry-config item))
   ;; Check envs: absent means all environments
   (let [envs (get-entry-envs item)]
     (or (nil? envs) (empty? envs) (some #{env} envs)))
   ;; Check modes: absent means all modes
   (let [modes (get-entry-modes item)]
     (or (nil? modes) (empty? modes) (some #{mode} modes)))))

(defn should-load-cljs-plugin?
  "Check if a CLJS plugin should be loaded (not a JS plugin)."
  [item env mode]
  (and
   (should-load-plugin? item env mode)
   ;; Is a CLJS plugin (or unspecified type)
   (let [item-type (get-entry-type item)]
     (or (nil? item-type) (= item-type "cljs")))))

(defn should-load-js-plugin?
  "Check if a JS plugin should be loaded."
  [item env mode]
  (and
   (should-load-plugin? item env mode)
   ;; Is a JS plugin
   (= "js" (get-entry-type item))
   ;; Is enabled (not explicitly disabled)
   (not (false? (get-entry-enabled item)))))

;; --- Plugin Resolution ---

(defn resolve-plugin [ns-str]
  (try
    (js/scittle.core.eval_string (str ns-str "/plugin"))
    (catch :default e
      (println "Warning: Could not resolve plugin in ns:" ns-str)
      nil)))

(defn get-cljs-plugins []
  "Gets all CLJS plugins from the manifest that match the current env/mode.
   Only attempts to resolve plugins whose files were actually loaded."
  (let [manifest (get-manifest)]
    (->> manifest
         ;; Filter to only CLJS plugins that should be loaded in current env/mode
         (filter #(should-load-cljs-plugin? % current-env current-mode))
         ;; Resolve the entry namespace
         (map #(resolve-plugin (get-entry-entry %)))
         (remove nil?)
         vec)))

(defn get-js-plugin-entries []
  "Gets manifest entries for JS plugins that should be loaded."
  (let [manifest (get-manifest)]
    (->> manifest
         (filter #(should-load-js-plugin? % current-env current-mode))
         vec)))

;; --- Embedded JS Plugin Support ---

(defn get-embedded-js-plugins-raw []
  "Gets JS plugins that were embedded in the single-file build.
   Returns the raw JS object (NOT converted to CLJS) so wrap-js-plugin can use aget."
  (when is-browser?
    (.-__PLIN_EMBEDDED_JS_PLUGINS__ js/window)))

(defn wrap-embedded-js-plugin
  "Wraps an embedded JS plugin definition into a CLJS plugin map.
   Takes a JS object with id, def, and files properties.
   The def must be a raw JS object for wrap-js-plugin to work correctly."
  [js-entry]
  (let [id (aget js-entry "id")
        js-def (aget js-entry "def")
        files (aget js-entry "files")
        ;; Create a manifest-like entry for the wrapper (as JS object with string keys)
        manifest-entry #js {"id" id "files" files}]
    (when js-def
      (js-loader/wrap-js-plugin js-def manifest-entry))))

(defn load-embedded-js-plugins []
  "Loads all embedded JS plugins (from single-file build).
   Returns a vector of wrapped CLJS plugin maps."
  (let [embedded-obj (get-embedded-js-plugins-raw)]
    (if (and embedded-obj (pos? (.-length (js/Object.keys embedded-obj))))
      (do
        (println "Found" (.-length (js/Object.keys embedded-obj)) "embedded JS plugins")
        (let [keys (js/Object.keys embedded-obj)]
          (->> (for [i (range (.-length keys))]
                 (let [k (aget keys i)
                       entry (aget embedded-obj k)]
                   (wrap-embedded-js-plugin entry)))
               (remove nil?)
               vec)))
      [])))

;; --- Expose Boot State for JS Plugins ---

(defn expose-boot-state!
  "Exposes the boot state on window.PLIN for JS plugins to access."
  []
  (when is-browser?
    (let [plin-obj (or (.-PLIN js/window) #js {})]
      (set! (.-boot plin-obj) 
            #js {:state boot/state
                 :reload (fn [] (boot/reload!))
                 :getPlugins (fn [] 
                               (clj->js (:all-plugins @boot/state)))
                 :getDisabledIds (fn []
                                   (clj->js (vec (:disabled-ids @boot/state))))})
      (set! (.-PLIN js/window) plin-obj))))

;; --- Main ---

(defn init []
  (when is-browser?
    (println "Loading plugins via System Bootstrapper...")
    (println "Booting in mode:" current-mode)
    
    (let [cljs-plugins (get-cljs-plugins)
          js-entries (get-js-plugin-entries)
          embedded-js-plugins (load-embedded-js-plugins)
          initially-disabled (get-initially-disabled-ids)]
      
      (println "Found" (count cljs-plugins) "CLJS plugins.")
      (println "Found" (count js-entries) "JS plugins to load (remote).")
      (println "Found" (count embedded-js-plugins) "JS plugins (embedded).")
      
      ;; Debug: print JS entries to verify they have string keys
      (when (seq js-entries)
        (js/console.log "JS plugin entries (first):" (clj->js (first js-entries))))
      
      ;; Check if we have embedded JS plugins (single-file build)
      (if (seq embedded-js-plugins)
        ;; Single-file build: use embedded plugins, no need to fetch
        (let [all-plugins (into cljs-plugins embedded-js-plugins)]
          (println "Using" (count embedded-js-plugins) "embedded JS plugins.")
          (boot/bootstrap! all-plugins initially-disabled)
          (expose-boot-state!))
        
        ;; Normal build: fetch JS plugins if any
        (if (empty? js-entries)
          ;; No JS plugins - bootstrap synchronously (backward compatible)
          (do
            (boot/bootstrap! cljs-plugins initially-disabled)
            (expose-boot-state!))
          
          ;; Has JS plugins - load them async then bootstrap
          ;; The promise resolves to a CLJS vector directly (not JS array)
          (.then (js-loader/load-js-plugins js-entries)
                 (fn [js-plugins]
                   ;; js-plugins is already a CLJS vector, no conversion needed
                   (println "Loaded" (count js-plugins) "JS plugins.")
                   ;; Debug: print first JS plugin structure
                   (when (seq js-plugins)
                     (js/console.log "First JS plugin:" (clj->js (first js-plugins))))
                   (let [all-plugins (into cljs-plugins js-plugins)]
                     (boot/bootstrap! all-plugins initially-disabled)
                     (expose-boot-state!)))
                 (fn [err]
                   (js/console.error "Failed to load JS plugins:" err)
                   ;; Fall back to CLJS-only plugins
                   (boot/bootstrap! cljs-plugins initially-disabled)
                   (expose-boot-state!))))))))

(init)
