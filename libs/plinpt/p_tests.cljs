(ns plinpt.p-tests
  (:require [plin.core :as plin]
            [plinpt.i-nav-bar :as inav]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-homepage :as ihome]
            [plinpt.p-nav-bar :as nav]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-tests.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing test pages and buggy components for testing error boundaries and plugin integration."
    :deps [inav/plugin nav/plugin iauth/plugin iapp/plugin ihome/plugin idev/plugin]
    
    :contributions
    {::inav/items [{:label "Test" :route "/test" :order 1}]
     ::iapp/routes [::test-route]

     ::ihome/features [{:title "Test Feature"
                        :description "This feature is provided by the tests plugin."
                        :color-class "bg-purple-600"
                        :href "/test"
                        :order 99}]
     
     ::idev/plugins [{:id :p-tests
                      :description "Test Pages."
                      :responsibilities "Provides pages for testing error boundaries and permissions."
                      :type :feature}]}

    :beans
    {::iauth/has-permission?
     [partial (fn [_] true)]

     ::iauth/has-all?
     [partial (fn [_] true)]

     ::iauth/has-any?
     [partial (fn [_] true)]

     ::iauth/can?
     [partial (fn [_] true)]

     ::iauth/user
     [atom {:id "test" :name "Test User"}]

     ::ui
     ^{:doc "Test page component"
       :reagent-component true}
     [partial core/create-test-page ::iauth/can?]

     ::test-route
     ^{:doc "Route for test page"}
     [core/make-route ::ui]

     ::buggy-widget
     ^{:doc "Buggy user widget"
       :reagent-component true}
     [:= core/buggy-component]}}))
