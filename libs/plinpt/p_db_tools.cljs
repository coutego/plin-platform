(ns plinpt.p-db-tools
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-devtools :as idev]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.i-service-invoker :as i-invoker]
            [plinpt.p-db-tools.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Database management dashboard."
    :deps [idev/plugin iapp/plugin i-invoker/plugin idevdoc/plugin]

    :contributions
    {::idev/items [{:title "DB Management"
                    :description "Administer, query and visualize the database."
                    :icon core/icon-db-cog
                    :href "/development/db"
                    :color-class "bg-purple-600"
                    :order 20}]
     
     ::iapp/routes [::route]

     ::idevdoc/plugins [{:id :db-tools
                         :description "DB Management Dashboard."
                         :responsibilities "Provides entry point for DB tools."
                         :type :tool}]}

    :beans
    {::ui
     ^{:doc "DB Dashboard Page"
       :reagent-component true}
     [partial core/dashboard-page ::i-invoker/reset-db]

     ::route
     ^{:doc "Route for DB Dashboard"}
     [core/make-route ::ui]}}))
