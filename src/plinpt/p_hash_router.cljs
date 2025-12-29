(ns plinpt.p-hash-router
  "Hash-based router implementation plugin.
   
   Provides client-side routing using browser hash (e.g., #/users/123).
   This is the default router for PLIN applications."
  (:require [plin.core :as plin]
            [plinpt.i-router :as irouter]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-hash-router.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Hash-based router implementation using browser hash for navigation."
    :deps [irouter/plugin iapp/plugin idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :p-hash-router
                      :description "Hash-based Router Implementation."
                      :responsibilities "Handles URL hash changes and provides reactive routing state."
                      :type :infrastructure}]}
    
    :beans
    {::irouter/current-route
     ^{:doc "Reactive atom containing the current matched route."
       :api {:ret :atom}}
     [:= core/current-route]
     
     ::irouter/navigate!
     ^{:doc "Function to navigate to a path by updating browser hash."
       :api {:args [["path" "/home" :string]] :ret :nil}}
     [:= core/navigate!]
     
     ::irouter/match-route
     ^{:doc "Function to match a path against registered routes."
       :api {:args [["path" "/home" :string]] :ret :map}}
     [core/make-match-route-fn]
     
     ::irouter/setup!
     ^{:doc "Function to initialize the router with routes and homepage."
       :api {:args [["homepage-path" "/" :string]] :ret :nil}}
     [core/make-setup-fn ::iapp/nav-routes]
     
     ::irouter/initialized?
     ^{:doc "Atom indicating whether the router has been initialized."
       :api {:ret :atom}}
     [:= core/initialized?]
     
     ;; Provide all-routes from nav-routes (the primary source)
     ::irouter/all-routes
     ^{:doc "All routes - sourced from i-application nav-routes."
       :api {:ret :vector}}
     [identity ::iapp/nav-routes]}}))
