(ns plinpt.p-tests
  (:require [plin.core :as plin]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-application :as iapp]
            [plinpt.i-homepage :as ihome]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-tests.core :as core]))

(defn icon-beaker []
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"}]])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing test pages and buggy components for testing error boundaries and plugin integration."
    :deps [iauth/plugin iapp/plugin ihome/plugin idev/plugin]
    
    :contributions
    {::iapp/nav-items [{:id :tests
                        :label "Tests"
                        :route "/test"
                        :icon icon-beaker
                        :component ::ui
                        :order 200}]

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

     ::buggy-widget
     ^{:doc "Buggy user widget"
       :reagent-component true}
     [:= core/buggy-component]}}))
