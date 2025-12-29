(ns plin-platform.client-boot
  "Client-side bootstrap entry point.
   
   Reads the manifest from window.SYSTEM_MANIFEST (set by index.html),
   resolves plugins, and calls boot/bootstrap!"
  (:require [plin.boot :as boot]
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

(defn get-manifest []
  (if is-browser?
    (js->clj (.-SYSTEM_MANIFEST js/window) :keywordize-keys true)
    []))

(defn get-initially-disabled-ids []
  (if is-browser?
    (let [raw-ids (.-INITIALLY_DISABLED_IDS js/window)
          ids (js->clj raw-ids)
          result (set (map keyword ids))]
      result)
    #{}))

;; --- Plugin Filtering ---

(defn should-load-plugin?
  "Check if a plugin should be loaded based on current env and mode.
   Load if: (envs absent OR current-env in envs) AND (modes absent OR current-mode in modes)"
  [item env mode]
  (and
   ;; Not a config entry
   (not (:config item))
   ;; Is a CLJS plugin
   (let [type (keyword (or (:type item) :cljs))]
     (= type :cljs))
   ;; Check envs: absent means all environments
   (let [envs (:envs item)]
     (or (nil? envs) (empty? envs) (some #{(keyword env)} (map keyword envs))))
   ;; Check modes: absent means all modes
   (let [modes (:modes item)]
     (or (nil? modes) (empty? modes) (some #{(keyword mode)} (map keyword modes))))))

;; --- Plugin Resolution ---

(defn resolve-plugin [ns-str]
  (try
    (js/scittle.core.eval_string (str ns-str "/plugin"))
    (catch :default e
      (println "Warning: Could not resolve plugin in ns:" ns-str)
      nil)))

(defn get-plugins []
  "Gets all plugins from the manifest that match the current env/mode.
   Only attempts to resolve plugins whose files were actually loaded."
  (let [manifest (get-manifest)]
    (println "Booting in mode:" current-mode)
    (->> manifest
         ;; Filter to only plugins that should be loaded in current env/mode
         (filter #(should-load-plugin? % current-env current-mode))
         ;; Resolve the entry namespace
         (map #(resolve-plugin (:entry %)))
         (remove nil?)
         vec)))

;; --- Main ---

(defn init []
  (when is-browser?
    (println "Loading plugins via System Bootstrapper...")
    (let [plugins (get-plugins)
          initially-disabled (get-initially-disabled-ids)]
      (println "Found" (count plugins) "plugins.")
      (boot/bootstrap! plugins initially-disabled))))

(init)
