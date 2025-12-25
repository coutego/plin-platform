(ns plinpt.p-development
  (:require [plin.core :as plin]
            [plinpt.i-development :as idev]
            [plinpt.i-devtools :as itools]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-development.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing development documentation page and integration with devtools and devdoc sections."
    :deps [idev/plugin itools/plugin iapp/plugin idevdoc/plugin]

    :contributions
    {;; Inject route into app shell
     ::iapp/routes [::route]

     ;; Inject into devtools
     ::itools/items [{:title "Documentation"
                      :description "Development resources and guidelines."
                      :icon core/icon-code
                      :color-class "bg-blue-600"
                      :href "/development/docs"
                      :order 1}]

     ;; Inject into dev-doc
     ::idevdoc/sections [{:title "Development Guidelines"
                          :content (fn []
                                     [:div {:class "space-y-4"}
                                      [:p "This section contains development guidelines and best practices for working with the plugin system."]
                                      [:p "Refer to the main technical documentation for detailed architecture information."]])}]
     
     ::idevdoc/plugins [{:id :development
                         :description "Development Documentation."
                         :responsibilities "Provides development guidelines and resources."
                         :type :infrastructure}]}

    :beans
    {::idev/ui
     ^{:doc "Development documentation page implementation."
       :reagent-component true}
     [partial core/create-development-page]

     ::route
     ^{:doc "Route definition for development docs"}
     [core/make-route ::idev/ui]}}))
