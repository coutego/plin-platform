(ns plinpt.i-head-config
  "Interface plugin for dynamic head configuration.
   
   This plugin provides extension points for injecting scripts, styles,
   and other resources into the document head at runtime.
   
   For BLOCKING resources that must load before the app, use the manifest config:
   ```clojure
   [{:config {:head {:scripts [\"https://critical.js\"]
                     :styles [\"https://critical.css\"]
                     :tailwind {:theme {:extend {...}}}}}}]
   ```
   
   For DYNAMIC resources that can load after the app starts, use this plugin's
   extension points. These are useful for:
   - Plugin-specific libraries
   - Optional features
   - Hot-loaded plugins"
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(defn- merge-tailwind-configs [db values]
  ;; Deep merge all tailwind configs, later values override earlier
  (let [merged (reduce (fn [acc cfg]
                         (merge-with (fn [a b]
                                       (if (and (map? a) (map? b))
                                         (merge a b)
                                         b))
                                     acc cfg))
                       {}
                       values)]
    (assoc-in db [:beans ::tailwind-config] [:= merged])))

(def plugin
  (plin/plugin
   {:doc "Interface for dynamic head configuration (scripts, styles, Tailwind).
          
          Use manifest :config :head for blocking resources.
          Use these extensions for dynamic/plugin-specific resources."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-head-config
                      :description "Interface for Head Configuration."
                      :responsibilities "Defines extension points for dynamic script/style injection."
                      :type :infrastructure}]}
    
    :extensions
    [{:key ::scripts
      :doc "Dynamic scripts to inject into <head>.
            Format: [{:src \"url\" :async? true :defer? false :type \"module\"}]
            or just [\"url1\" \"url2\"] for simple cases.
            These load AFTER the app starts (non-blocking)."
      :handler (plin/collect-data ::scripts)}
     
     {:key ::styles
      :doc "Dynamic stylesheets to inject into <head>.
            Format: [{:href \"url\" :media \"screen\"}]
            or just [\"url1\" \"url2\"] for simple cases."
      :handler (plin/collect-data ::styles)}
     
     {:key ::inline-styles
      :doc "Inline CSS to inject into <head>.
            Format: [\"css-string-1\" \"css-string-2\"]
            All strings are concatenated."
      :handler (plin/collect-data ::inline-styles)}
     
     {:key ::tailwind-config
      :doc "Tailwind configuration extensions.
            Format: {:theme {:extend {:colors {...}}}}
            Multiple contributions are deep-merged."
      :handler merge-tailwind-configs}
     
     {:key ::meta-tags
      :doc "Meta tags to inject into <head>.
            Format: [{:name \"description\" :content \"...\"}
                     {:property \"og:title\" :content \"...\"}]"
      :handler (plin/collect-data ::meta-tags)}]
    
    :beans
    {::scripts
     ^{:doc "Collected dynamic scripts to inject."
       :api {:ret :vector}}
     [:= []]
     
     ::styles
     ^{:doc "Collected dynamic stylesheets to inject."
       :api {:ret :vector}}
     [:= []]
     
     ::inline-styles
     ^{:doc "Collected inline CSS strings."
       :api {:ret :vector}}
     [:= []]
     
     ::tailwind-config
     ^{:doc "Merged Tailwind configuration from all plugins."
       :api {:ret :map}}
     [:= {}]
     
     ::meta-tags
     ^{:doc "Collected meta tags to inject."
       :api {:ret :vector}}
     [:= []]
     
     ::inject!
     ^{:doc "Function to inject all collected head resources into the DOM.
             Called automatically by p-head-config on mount.
             Can be called manually to re-inject after hot-reload."
       :api {:args [] :ret :nil}}
     [:= (fn [] nil)]}}))
