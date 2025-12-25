(ns plin-platform.client-boot
  (:require [plin.boot :as boot]
            [clojure.string :as str]))

(def is-browser? 
  (try
    (some? js/window)
    (catch :default _ false)))

;; Default to "demo" mode if not specified
(def current-mode 
  (if is-browser?
    (or (.-APP_MODE js/window) "demo")
    "demo"))

;; Define the profiles (which tags are active for which mode)
(def profiles
  {"demo"   #{:shared :ui :demo}
   "client" #{:shared :ui :client}
   "server" #{:shared :server}})

(defn get-manifest []
  ;; In browser, we expect this to be loaded into window.SYSTEM_MANIFEST
  (if is-browser?
    (js->clj (.-SYSTEM_MANIFEST js/window) :keywordize-keys true)
    []))

(defn resolve-plugin [ns-str]
  (try
    (js/scittle.core.eval_string (str ns-str "/plugin"))
    (catch :default e
      (println "Warning: Could not resolve plugin in ns:" ns-str)
      nil)))

(defn get-plugins [mode]
  (let [manifest (get-manifest)
        active-tags (get profiles mode #{})]
    (println "Booting in mode:" mode)
    (->> manifest
         ;; Filter plugins that have at least one active tag
         (filter (fn [item]
                   (let [item-tags (set (map keyword (:tags item)))]
                     (some active-tags item-tags))))
         ;; Filter out non-CLJS plugins (default is :cljs)
         (filter (fn [item]
                   (let [type (keyword (or (:type item) :cljs))]
                     (= type :cljs))))
         ;; Resolve the entry namespace
         (map #(resolve-plugin (:entry %)))
         (remove nil?)
         vec)))

(defn init []
  (when is-browser?
    (println "Loading plugins via System Bootstrapper...")
    (let [plugins (get-plugins current-mode)]
      (println "Found" (count plugins) "plugins.")
      (boot/bootstrap! plugins))))

(init)
