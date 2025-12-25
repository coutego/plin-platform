(ns plinpt.i-dynamic-loader
  (:require [plin.core :as plin]
            [plinpt.i-devtools :as devtools]
            [plinpt.i-app-shell :as app-shell]))

(def plugin
  (plin/plugin
   {:doc "Interface for Dynamic Plugin Loader."
    :deps [devtools/plugin app-shell/plugin]
    
    :extensions
    [{:key ::handlers
      :doc "List of file handlers. Schema: {:extension string :handler fn}"
      :handler (plin/collect-all ::handlers)}]

    :beans
    {::handlers
     ^{:doc "List of registered file handlers."
       :api {:ret :vector}}
     [:= []]}}))
