(ns plinpt.p-devtools
  (:require [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-app-shell :as iapp-shell]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-devtools.core :as core]))

(def icon-tools
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M8 9l3 3-3 3m5 0h3M5 20h14a2 2 0 002-2V6a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"}]])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing developer tools page and integration with home features."
    :deps [iapp/plugin idevdoc/plugin iapp-shell/plugin]

    :contributions
    {;; Register Development Section in Sidebar (root level)
     ::iapp/nav-items [{:id :development
                        :label "Development"
                        :description "Tools for developers."
                        :route "/development"
                        :icon icon-tools
                        :icon-color "text-gray-700 bg-gray-100"
                        :order 200}]
     
     ::idevdoc/plugins [{:id :devtools
                         :description "Developer Tools Implementation."
                         :responsibilities "Provides the dev tools dashboard."
                         :type :infrastructure}]}

    :beans
    {::ui
     ^{:doc "Developer tools page component implementation."
       :reagent-component true}
     [partial core/create-dev-tools-page []]}}))
