(ns plinpt.i-development
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for development documentation, defining the main UI component for development pages."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-development
                      :description "Interface for Development Docs."
                      :responsibilities "Defines the UI bean for dev docs."
                      :type :infrastructure}]}

    :beans
    {::ui
     ^{:doc "Development documentation page component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= [:div "Development Docs"]]}}))
