(ns plinpt.p-db-tools
  (:require [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.i-service-invoker :as i-invoker]
            [plinpt.p-alasql-explorer :as alasql-explorer]
            [plinpt.p-schema-visualizer :as schema-viz]
            [plinpt.p-db-tools.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Database management dashboard."
    :deps [iapp/plugin i-invoker/plugin idevdoc/plugin alasql-explorer/plugin schema-viz/plugin]

    :contributions
    {::iapp/nav-items [::nav-root ::nav-admin ::nav-data ::nav-schema]

     ::idevdoc/plugins [{:id :db-tools
                         :description "DB Management Dashboard."
                         :responsibilities "Provides entry point for DB tools."
                         :type :tool}]}

    :beans
    {::ui
     ^{:doc "DB Dashboard Page"
       :reagent-component true}
     [:= core/dashboard-page]

     ::admin-ui
     ^{:doc "DB Admin Page"
       :reagent-component true}
     [partial core/admin-page ::i-invoker/reset-db]
     
     ::nav-root
     ^{:doc "Root Nav item"}
     {:constructor [(fn [ui]
                      {:id :db-tools
                       :parent-id :development
                       :label "DB Management"
                       :description "Manage database schema and data."
                       :route "db"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"}]]
                       :icon-color "text-emerald-600 bg-emerald-50"
                       :component ui
                       :order 20})
                    ::ui]}

     ::nav-admin
     ^{:doc "Admin Nav item"}
     {:constructor [(fn [ui]
                      {:id :db-admin
                       :parent-id :db-tools
                       :label "DB Administration"
                       :description "Reset and manage DB."
                       :route "admin"
                       :component ui
                       :order 1})
                    ::admin-ui]}

     ::nav-data
     ^{:doc "Data Nav item"}
     {:constructor [(fn [ui]
                      {:id :db-data
                       :parent-id :db-tools
                       :label "Data Management"
                       :description "AlaSQL Explorer."
                       :route "alasql"
                       :component ui
                       :order 2})
                    ::alasql-explorer/ui]}

     ::nav-schema
     ^{:doc "Schema Nav item"}
     {:constructor [(fn [ui]
                      {:id :db-schema
                       :parent-id :db-tools
                       :label "Schema Visualization"
                       :description "ER Diagram."
                       :route "schema"
                       :component ui
                       :order 3})
                    ::schema-viz/ui]}}}))
