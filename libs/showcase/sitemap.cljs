(ns showcase.sitemap
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devtools :as devtools]))

;; --- State ---

(defonce state (r/atom {:search ""}))

;; --- Components ---

(defn icon-sitemap []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"}]])

(defn sitemap-page [boot-api]
  (let [sys-state (:state boot-api)
        container (:container @sys-state)
        routes (get container ::iapp/routes [])
        search (:search @state)
        filtered-routes (if (str/blank? search)
                          routes
                          (filter (fn [r]
                                    (or (str/includes? (str/lower-case (or (:path r) "")) (str/lower-case search))
                                        (when (:doc r) (str/includes? (str/lower-case (:doc r)) (str/lower-case search)))))
                                  routes))
        sorted-routes (sort-by :path filtered-routes)]
    [:div {:class "p-6 max-w-6xl mx-auto"}
     [:div {:class "flex items-center gap-4 mb-6"}
      [:div {:class "p-3 bg-indigo-100 text-indigo-600 rounded-lg"}
       [icon-sitemap]]
      [:div
       [:h1 {:class "text-2xl font-bold text-slate-800"} "Application Sitemap"]
       [:p {:class "text-slate-500"} "Overview of all registered routes in the application."]]]

     ;; Search
     [:div {:class "mb-6"}
      [:div {:class "relative"}
       [:div {:class "absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none"}
        [:svg {:class "h-5 w-5 text-gray-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
         [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"}]]]
       [:input {:type "text"
                :class "block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md leading-5 bg-white placeholder-gray-500 focus:outline-none focus:placeholder-gray-400 focus:ring-1 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                :placeholder "Search routes..."
                :value search
                :on-change #(swap! state assoc :search (.. % -target -value))}]]]

     ;; Table
     [:div {:class "bg-white shadow overflow-hidden border-b border-gray-200 sm:rounded-lg"}
      [:table {:class "min-w-full divide-y divide-gray-200"}
       [:thead {:class "bg-gray-50"}
        [:tr
         [:th {:scope "col" :class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Path"]
         [:th {:scope "col" :class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Permission"]
         [:th {:scope "col" :class "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"} "Layout"]
         [:th {:scope "col" :class "px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider"} "Action"]]]
       [:tbody {:class "bg-white divide-y divide-gray-200"}
        (for [route sorted-routes]
          ^{:key (:path route)}
          [:tr {:class "hover:bg-gray-50 transition-colors"}
           [:td {:class "px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900 font-mono"}
            (:path route)]
           [:td {:class "px-6 py-4 whitespace-nowrap text-sm text-gray-500"}
            (if (:required-perm route)
              [:span {:class "px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-yellow-100 text-yellow-800"}
               (str (:required-perm route))]
              [:span {:class "px-2 inline-flex text-xs leading-5 font-semibold rounded-full bg-green-100 text-green-800"}
               "Public"])]
           [:td {:class "px-6 py-4 whitespace-nowrap text-sm text-gray-500"}
            (or (:layout route) "Default")]
           [:td {:class "px-6 py-4 whitespace-nowrap text-right text-sm font-medium"}
            [:a {:href (str "#" (:path route))
                 :class "text-indigo-600 hover:text-indigo-900"}
             "Visit"]]])]]]]))

(def plugin
  (plin/plugin
   {:doc "Sitemap visualization plugin."
    :deps [iapp/plugin devtools/plugin boot/plugin]
    
    :contributions
    {::iapp/routes [::route]
     ::devtools/items [{:title "Sitemap"
                        :description "Visual map of application routes."
                        :icon icon-sitemap
                        :color-class "bg-purple-500"
                        :href "/showcase/sitemap"
                        :order 60}]}

    :beans
    {::route
     ^{:doc "Sitemap route definition."
       :api {:ret :map}}
     [(fn [boot-api]
        {:path "/showcase/sitemap"
         :component (partial sitemap-page boot-api)})
      ::boot/api]}}))
