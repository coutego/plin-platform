(ns plinpt.p-sidebar
  (:require [plin.core :as plin]
            [plinpt.i-sidebar :as isidebar]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-sidebar.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of the Drill-Down Sidebar."
    :deps [isidebar/plugin iapp/plugin idev/plugin]

    :contributions
    {::idev/plugins [{:id :p-sidebar
                      :description "Drill-Down Sidebar Implementation."
                      :responsibilities "Renders the application tree as a drill-down menu."
                      :type :feature}]}

    :beans
    {::isidebar/ui
     ^{:doc "The Drill-Down Sidebar Component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/sidebar-component ::iapp/structure]
     
     ::isidebar/toggle!
     ^{:doc "Function to toggle the sidebar collapsed state."
       :api {:args [] :ret :nil}}
     [:= core/toggle-sidebar!]
     
     ::isidebar/collapsed?
     ^{:doc "Function to check if the sidebar is collapsed."
       :api {:args [] :ret :boolean}}
     [:= core/sidebar-collapsed?]}}))
