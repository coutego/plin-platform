(ns plinpt.p-devtools.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defn icon-tools []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]])

(defn icon-search []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"}]])

(defn tool-card [item]
  (let [href (:href item)]
    [:a {:href (if (and href (string? href) (str/starts-with? href "/"))
                 (str "#" href)
                 href)
         :class "block bg-white overflow-hidden shadow rounded-lg hover:shadow-md transition-shadow duration-200 cursor-pointer hover:bg-gray-50"}
     [:div {:class "px-4 py-5 sm:p-6"}
      [:div {:class "flex items-center mb-4"}
       [:div {:class (str "flex-shrink-0 rounded-md p-2 " (or (:color-class item) "bg-gray-500"))}
        (if (:icon item)
          [(:icon item)]
          [icon-tools])]
       [:h3 {:class "ml-3 text-lg font-medium text-gray-900"} (:title item)]]
      [:p {:class "text-sm text-gray-500"} (:description item)]]]))

(defn dev-tools-page [items]
  ;; Filter out items that are not maps (e.g. unresolved bean keys) to prevent crashes
  (let [sorted-items (->> items
                          (filter map?)
                          (sort-by :order))]
    [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
     [:div {:class "text-center mb-12"}
      [:h2 {:class "text-3xl font-extrabold text-gray-900 sm:text-4xl"}
       "Developer Tools"]
      [:p {:class "mt-4 text-lg text-gray-500"}
       "Tools and documentation for developers working on the PLIN Demo application."]]

     [:div {:class "grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"}
      (if (empty? sorted-items)
        [:div {:class "col-span-full text-center text-gray-500 italic py-10"}
         "No developer tools registered."]
        (for [[idx item] (map-indexed vector sorted-items)]
          ^{:key idx} [tool-card item]))]]))

(defn create-dev-tools-page [items]
  (fn [] [dev-tools-page items]))
