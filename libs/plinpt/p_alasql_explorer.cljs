(ns plinpt.p-alasql-explorer
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-devtools :as idev]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-alasql-explorer.core :as core]))

(def plugin
  (plin/plugin
   {:doc "AlaSQL Database Explorer and Runner."
    :deps [idev/plugin iapp/plugin idevdoc/plugin]

    :contributions
    {;; Removed direct contribution to devtools items
     ::iapp/routes [::route]

     ::idevdoc/plugins [{:id :alasql-explorer
                         :description "AlaSQL Explorer Tool."
                         :responsibilities "Provides UI to query the in-memory database."
                         :type :tool}]}

    :beans
    {::ui
     ^{:doc "AlaSQL Explorer Page"
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/explorer-page]

     ::route
     ^{:doc "Route for AlaSQL Explorer"
       :api {:ret :map}}
     [core/make-route ::ui]}}))
