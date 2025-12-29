(ns plinpt.i-sidebar
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for the Sidebar UI component."
    :deps [idev/plugin]

    :contributions
    {::idev/plugins [{:id :i-sidebar
                      :description "Interface for Sidebar UI."
                      :responsibilities "Defines the UI bean for the sidebar and sidebar actions."
                      :type :infrastructure}]}

    :beans
    {::ui
     ^{:doc "The Sidebar UI component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]
     
     ::toggle!
     ^{:doc "Function to toggle the sidebar collapsed state."
       :api {:args [] :ret :nil}}
     [:= (fn [] (js/console.warn "No sidebar implementation: toggle!"))]
     
     ::collapsed?
     ^{:doc "Function to check if the sidebar is collapsed."
       :api {:args [] :ret :boolean}}
     [:= (fn [] false)]}}))
