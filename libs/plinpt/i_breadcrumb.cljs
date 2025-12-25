(ns plinpt.i-breadcrumb
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for Breadcrumb navigation."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-breadcrumb
                      :description "Interface for Breadcrumbs."
                      :responsibilities "Defines the UI bean for the breadcrumb trail."
                      :type :infrastructure}]}

    :beans
    {::ui
     ^{:doc "The Breadcrumb UI component. Renders a navigation trail based on browser history."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]}}))
