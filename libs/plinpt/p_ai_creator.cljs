(ns plinpt.p-ai-creator
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devtools :as devtools]
            [plinpt.p-ai-creator.ui :as ui]))

(def plugin
  (plin/plugin
   {:doc "AI Plugin Creator."
    :deps [iapp/plugin devtools/plugin boot/plugin]
    
    :contributions
    {::iapp/routes [::route]
     ::devtools/items [{:title "AI Plugin Creator"
                        :description "Generate plugins using AI."
                        :icon ui/icon-magic
                        :color-class "bg-purple-600"
                        :href "/development/ai-creator"
                        :order 10}]}

    :beans
    {::route
     ^{:doc "Route for AI Creator."
       :api {:ret :map}}
     [(fn [boot-api]
        {:path "/development/ai-creator"
         :component (partial ui/page boot-api)})
      ::boot/api]}}))
