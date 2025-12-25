(ns plinpt.p-development.core
  (:require [reagent.core :as r]
            [plinpt.p-devdoc.core :as doc]))

(defn icon-code []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]])

(defn highlight-code [code]
  (let [tokens (re-seq #"(\"[^\"]*\")|(;.*)|(:[\w\-\.\/]+)|(\b\d+\b)|([\[\]\{\}\(\)])|(\s+)|([^\"\s;:\d\[\]\{\}\(\)]+)" code)]
    [:pre {:class "bg-gray-800 text-gray-100 p-3 rounded text-xs overflow-x-auto font-mono"}
     (for [[idx [match str-lit comment kw num bracket space other]] (map-indexed vector tokens)]
       ^{:key idx}
       (cond
         str-lit [:span {:class "text-green-400"} match]
         comment [:span {:class "text-gray-500 italic"} match]
         kw      [:span {:class "text-purple-400"} match]
         num     [:span {:class "text-orange-400"} match]
         bracket [:span {:class "text-yellow-500"} match]
         space   [:span match]
         other   (if (re-matches #"^def|^defn|^let|^if|^when|^cond|^assoc|^update|^->|^apply|^map|^reduce" match)
                   [:span {:class "text-blue-400 font-bold"} match]
                   [:span {:class "text-gray-200"} match])
         :else   [:span match]))]))

(defn development-page []
  [:div {:class "max-w-7xl mx-auto py-6 sm:px-6 lg:px-8"}
   [:div {:class "mb-8"}
    [:h2 {:class "text-3xl font-extrabold text-gray-900"} "Development Documentation"]
    [:p {:class "mt-2 text-lg text-gray-500"}
     "Resources and tools for developers."]]
   
   [:div {:class "bg-white shadow overflow-hidden sm:rounded-lg p-6"}
    [:div {:class "space-y-6"}
     [:div
      [:h3 {:class "text-xl font-semibold text-gray-900 mb-3"} "Getting Started"]
      [:ul {:class "list-disc pl-5 space-y-2 text-gray-700"}
       [:li "Review the " [:a {:href "#/dev-doc" :class "text-blue-600 hover:text-blue-800"} "Technical Documentation"] " for architecture overview"]
       [:li "Check the " [:a {:href "#/demo" :class "text-blue-600 hover:text-blue-800"} "Live Demo"] " to see features in action"]
       [:li "Explore " [:a {:href "#/development" :class "text-blue-600 hover:text-blue-800"} "Developer Tools"] " for debugging"]]]
     
     [:div
      [:h3 {:class "text-xl font-semibold text-gray-900 mb-3"} "Development Guidelines"]
      [:ul {:class "list-disc pl-5 space-y-2 text-gray-700"}
       [:li "Follow the " [:a {:href "#/dev-doc" :class "text-blue-600 hover:text-blue-800"} "Plugin Architecture"] " patterns"]
       [:li "Use standard UI components from the ui-components plugin"]
       [:li "Implement features as plugins with proper dependency injection"]
       [:li "Test features in the demo environment before deployment"]]]
     
     [:div
      [:h3 {:class "text-xl font-semibold text-gray-900 mb-3"} "Plugin System"]
      [:p {:class "mb-3"} "The application uses a plugin-based architecture with dependency injection. Key concepts:"]
      [:ul {:class "list-disc pl-5 space-y-2 text-gray-700"}
       [:li [:strong "Plugins"] ": Self-contained units that provide functionality"]
       [:li [:strong "Extension Points"] ": Places where plugins can contribute data or components"]
       [:li [:strong "Beans"] ": Injectable dependencies (functions, atoms, components)"]
       [:li [:strong "Dependencies"] ": Plugins declare what other plugins they depend on"]]]]]])

(defn create-development-page []
  (fn [] [development-page]))

(defn make-route [ui]
  {:path "/development/docs" :component ui})
