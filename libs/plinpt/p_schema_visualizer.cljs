(ns plinpt.p-schema-visualizer
  (:require [plin.core :as plin]
            [plinpt.i-devtools :as idev]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-schema-visualizer.ui :as ui]))

(def plugin
  (plin/plugin
   {:doc "Visualizes the AlaSQL database schema using Mermaid.js."
    :deps [idev/plugin iapp/plugin idevdoc/plugin]

    :contributions
    {;; Removed direct contribution to devtools items
     ::iapp/routes [::route]

     ::idevdoc/plugins [{:id :schema-visualizer
                         :description "Schema Visualizer."
                         :responsibilities "Generates ER diagrams from DB metadata."
                         :type :tool}]}

    :beans
    {::ui
     ^{:doc "Schema Diagram Page"}
     [partial ui/diagram-view]

     ::route
     ^{:doc "Route for Schema Visualizer"}
     [ui/make-route ::ui]}}))
