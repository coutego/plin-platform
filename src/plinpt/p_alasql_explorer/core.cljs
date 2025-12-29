(ns plinpt.p-alasql-explorer.core
  (:require [reagent.core :as r]))

(defonce state (r/atom {:tables []
                        :sql ""
                        :results nil
                        :error nil
                        :page 0
                        :page-size 10}))

(defn- exec-sql! []
  (let [sql (:sql @state)]
    (try
      (let [res (js/alasql sql)
            data (js->clj res :keywordize-keys true)]
        (swap! state assoc :results data :error nil :page 0))
      (catch :default e
        (swap! state assoc :error (.-message e) :results nil)))))

(defn- load-tables! []
  (try
    ;; Ensure we are using the demo db if it exists, otherwise just show what's there
    (try (js/alasql "USE pluggable_demo_db") (catch :default _ nil))
    
    (let [res (js/alasql "SHOW TABLES")
          tables (map :tableid (js->clj res :keywordize-keys true))]
      (swap! state assoc :tables tables))
    (catch :default e
      (js/console.error "Failed to load tables" e))))

(defn- select-table [t]
  (let [sql (str "SELECT * FROM " t)]
    (swap! state assoc :sql sql)
    (exec-sql!)))

(defn- change-page [delta]
  (swap! state update :page + delta))

(defn- set-page-size [size]
  (swap! state assoc :page-size (js/parseInt size) :page 0))

(defn explorer-page []
  (r/create-class
   {:component-did-mount load-tables!
    :reagent-render
    (fn []
      (let [{:keys [tables sql results error page page-size]} @state
            is-seq? (sequential? results)
            total-results (if is-seq? (count results) 0)
            total-pages (if (pos? total-results) (Math/ceil (/ total-results page-size)) 0)
            start-idx (* page page-size)
            end-idx (min (+ start-idx page-size) total-results)
            current-page-data (if is-seq? 
                                (subvec (vec results) start-idx end-idx) 
                                results)]
        [:div {:class "flex h-[calc(100vh-64px)]"}
         ;; Sidebar
         [:div {:class "w-64 bg-gray-50 border-r border-gray-200 overflow-y-auto flex-shrink-0"}
          [:div {:class "p-4 font-bold text-gray-700 border-b flex justify-between items-center"} 
           "Tables"
           [:button {:class "text-xs text-blue-600 hover:text-blue-800" :on-click load-tables!} "Refresh"]]
          [:ul
           (if (empty? tables)
             [:li {:class "p-4 text-sm text-gray-400 italic"} "No tables found."]
             (for [t tables]
               ^{:key t}
               [:li {:class "cursor-pointer hover:bg-blue-50 p-3 text-sm text-gray-600 border-b border-gray-100 transition-colors"
                     :on-click #(select-table t)}
                t]))]]
         
         ;; Main Content
         [:div {:class "flex-1 flex flex-col overflow-hidden min-w-0"}
          ;; Editor
          [:div {:class "p-4 border-b bg-white shadow-sm z-10"}
           [:div {:class "mb-2 font-bold text-gray-700"} "SQL Query"]
           [:div {:class "flex gap-2"}
            [:textarea {:class "flex-1 p-2 border rounded font-mono text-sm h-24 focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none resize-none"
                        :value sql
                        :on-change #(swap! state assoc :sql (-> % .-target .-value))
                        :placeholder "Enter SQL query here..."}]
            [:button {:class "bg-blue-600 text-white px-6 py-2 rounded hover:bg-blue-700 font-medium self-start shadow-sm transition-colors"
                      :on-click exec-sql!}
             "Run"]]]
          
          ;; Results
          [:div {:class "flex-1 overflow-auto p-4 bg-gray-50 flex flex-col"}
           (cond
             error
             [:div {:class "bg-red-50 border-l-4 border-red-500 p-4 text-red-700 rounded shadow-sm"}
              [:p {:class "font-bold"} "Error"]
              [:p {:class "font-mono text-sm mt-1"} error]]

             (nil? results)
             [:div {:class "text-gray-500 italic text-center mt-10"} "Enter a query and click Run."]

             (and is-seq? (empty? results))
             [:div {:class "text-gray-500 italic text-center mt-10"} "Query returned no rows."]

             is-seq?
             [:div {:class "flex flex-col h-full"}
              [:div {:class "bg-white shadow rounded overflow-hidden border border-gray-200 flex-1 overflow-auto"}
               [:div {:class "overflow-x-auto"}
                [:table {:class "min-w-full divide-y divide-gray-200"}
                 [:thead {:class "bg-gray-50 sticky top-0"}
                  (let [cols (keys (first results))]
                    [:tr
                     (for [c cols]
                       ^{:key c}
                       [:th {:class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap bg-gray-50"} c])])]
                 [:tbody {:class "bg-white divide-y divide-gray-200"}
                  (let [cols (keys (first results))]
                    (for [[i row] (map-indexed vector current-page-data)]
                      ^{:key i}
                      [:tr {:class "hover:bg-gray-50"}
                       (for [c cols]
                         ^{:key (str i "-" c)}
                         [:td {:class "px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono"}
                          (str (get row c))])]))]]]]
              
              ;; Pagination Controls
              [:div {:class "mt-4 flex items-center justify-between bg-white p-3 rounded shadow border border-gray-200"}
               [:div {:class "flex items-center text-sm text-gray-700"}
                [:span "Showing " [:span {:class "font-medium"} (inc start-idx)] " to " [:span {:class "font-medium"} end-idx] " of " [:span {:class "font-medium"} total-results] " results"]
                [:select {:class "ml-4 border-gray-300 rounded text-sm focus:ring-blue-500 focus:border-blue-500 p-1"
                          :value page-size
                          :on-change #(set-page-size (-> % .-target .-value))}
                 [:option {:value 10} "10 per page"]
                 [:option {:value 25} "25 per page"]
                 [:option {:value 50} "50 per page"]
                 [:option {:value 100} "100 per page"]]]
               
               [:div {:class "flex gap-2"}
                [:button {:class "px-3 py-1 border rounded text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                          :disabled (<= page 0)
                          :on-click #(change-page -1)}
                 "Previous"]
                [:button {:class "px-3 py-1 border rounded text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
                          :disabled (>= (inc page) total-pages)
                          :on-click #(change-page 1)}
                 "Next"]]]]

             :else
             [:div {:class "bg-green-50 border-l-4 border-green-500 p-4 text-green-700 rounded shadow-sm"}
              [:span {:class "font-bold mr-2"} "Result:"]
              [:span {:class "font-mono"} (str results)]])]]]))}))

(defn make-route [ui]
  {:path "/development/alasql" :component ui :layout :full-screen})
