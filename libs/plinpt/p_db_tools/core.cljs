(ns plinpt.p-db-tools.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defn icon-db-cog []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]])

(defn icon-reset []
  [:svg {:class "h-6 w-6 text-red-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"}]])

(defn icon-table []
  [:svg {:class "h-6 w-6 text-blue-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M3 10h18M3 14h18m-9-4v8m-7 0h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"}]])

(defn icon-chart []
  [:svg {:class "h-6 w-6 text-pink-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z"}]])

(defn reset-db-card [reset-fn]
  [:div {:class "bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200"}
   [:div {:class "px-4 py-5 sm:p-6"}
    [:div {:class "flex items-center mb-4"}
     [:div {:class "flex-shrink-0 rounded-md p-2 bg-red-100"}
      [icon-reset]]
     [:h3 {:class "ml-3 text-lg font-medium text-gray-900"} "DB Administration"]]
    [:p {:class "text-sm text-gray-500 mb-4"} "Reset the database to its initial state. This will delete all data."]
    [:button {:class "bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 text-sm font-medium"
              :on-click #(when (js/confirm "Are you sure you want to reset the database? All data will be lost.")
                           (reset-fn)
                           (js/alert "Database reset complete."))}
     "Reset Database"]]])

(defn tool-link-card [title desc icon href]
  [:a {:href (if (str/starts-with? href "/") (str "#" href) href)
       :class "block bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 cursor-pointer"}
   [:div {:class "px-4 py-5 sm:p-6"}
    [:div {:class "flex items-center mb-4"}
     [:div {:class "flex-shrink-0 rounded-md p-2 bg-gray-100"}
      icon]
     [:h3 {:class "ml-3 text-lg font-medium text-gray-900"} title]]
    [:p {:class "text-sm text-gray-500"} desc]]])

(defn dashboard-page [reset-fn]
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [:div {:class "text-center mb-12"}
    [:h2 {:class "text-3xl font-extrabold text-gray-900 sm:text-4xl"}
     "Database Management"]
    [:p {:class "mt-4 text-lg text-gray-500"}
     "Manage your local AlaSQL database."]]

   [:div {:class "grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"}
    [reset-db-card reset-fn]
    [tool-link-card "Data Management" "Inspect tables and run SQL queries." [icon-table] "/development/alasql"]
    [tool-link-card "Schema Visualization" "View Entity-Relationship diagram." [icon-chart] "/development/schema"]]])

(defn make-route [ui]
  {:path "/development/db" :component ui})
