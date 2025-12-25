(ns plinpt.p-devtools
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-devtools :as idev]
            [plinpt.i-homepage :as ihome]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-devtools.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing developer tools page and integration with home features."
    :deps [idev/plugin ihome/plugin iapp/plugin idevdoc/plugin]

    :contributions
    {;; Inject route into app shell
     ::iapp/routes [::route]

     ;; Add link to home page
     ::ihome/features [{:title "Development"
                        :description "Tools for developers"
                        :icon core/icon-tools
                        :color-class "bg-gray-800"
                        :href "/development"
                        :order 100}]
     
     ;; Add Tracer to DevTools
     ::idev/items [{:title "Service Tracer"
                    :description "Trace service calls and SQL execution."
                    :icon core/icon-search
                    :color-class "bg-blue-600"
                    :href "/tracer"
                    :order 20}]
     
     ::idevdoc/plugins [{:id :devtools
                         :description "Developer Tools Implementation."
                         :responsibilities "Provides the dev tools dashboard."
                         :type :infrastructure}]}

    :beans
    {::idev/ui
     ^{:doc "Developer tools page component implementation."
       :reagent-component true}
     [partial core/create-dev-tools-page ::idev/items]

     ::route
     ^{:doc "Route definition for dev tools page"}
     [core/make-route ::idev/ui]}}))
