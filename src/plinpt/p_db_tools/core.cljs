(ns plinpt.p-db-tools.core
  (:require [clojure.string :as str]))

(defn icon-reset []
  [:svg {:class "h-6 w-6 text-red-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"}]])

(defn icon-table []
  [:svg {:class "h-6 w-6 text-blue-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3 10h18M3 14h18m-9-4v8m-7 0h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"}]])

(defn icon-chart []
  [:svg {:class "h-6 w-6 text-pink-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z"}]])

(defn tool-link-card [title desc icon href]
  [:a {:href (str "#/development/db/" href)
       :class "block bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 cursor-pointer"}
   [:div {:class "px-4 py-5 sm:p-6"}
    [:div {:class "flex items-center mb-4"}
     [:div {:class "flex-shrink-0 rounded-md p-2 bg-gray-100"}
      icon]
     [:h3 {:class "ml-3 text-lg font-medium text-gray-900"} title]]
    [:p {:class "text-sm text-gray-500"} desc]]])

(defn dashboard-page []
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [:div {:class "mb-8"}
    [:h2 {:class "text-2xl font-bold text-gray-900"} "Database Tools"]
    [:p {:class "mt-1 text-sm text-gray-500"} "Manage your local database, visualize schema, and explore data."]]
   
   [:div {:class "grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"}
    [tool-link-card "Administration" "Reset and manage the database." [icon-reset] "admin"]
    [tool-link-card "Data Explorer" "Execute SQL queries and view data." [icon-table] "alasql"]
    [tool-link-card "Schema Visualization" "View Entity-Relationship diagram." [icon-chart] "schema"]]])

(defn admin-page [reset-fn]
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [:div {:class "mb-8"}
    [:h2 {:class "text-2xl font-bold text-gray-900"} "Database Administration"]
    [:p {:class "mt-1 text-sm text-gray-500"} "Administrative tasks for the local database."]]

   [:div {:class "bg-white shadow sm:rounded-lg"}
    [:div {:class "px-4 py-5 sm:p-6"}
     [:h3 {:class "text-lg leading-6 font-medium text-gray-900"} "Reset Database"]
     [:div {:class "mt-2 max-w-xl text-sm text-gray-500"}
      [:p "Reset the database to its initial state. This will delete all data and re-initialize it from the plugin definitions."]]
     [:div {:class "mt-5"}
      [:button {:type "button"
                :class "inline-flex items-center justify-center px-4 py-2 border border-transparent font-medium rounded-md text-red-700 bg-red-100 hover:bg-red-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 sm:text-sm"
                :on-click #(when (js/confirm "Are you sure you want to reset the database? All data will be lost.")
                             (reset-fn)
                             (js/alert "Database reset complete."))}
       "Reset Database"]]]]])
