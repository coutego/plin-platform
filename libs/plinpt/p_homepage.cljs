(ns plinpt.p-homepage
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-homepage :as ihome]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-homepage.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing the main home page with error boundaries, guest content, and feature display."
    :deps [iauth/plugin ihome/plugin iapp/plugin idev/plugin]

    :contributions
    {;; Inject route into app shell
     ::iapp/routes [::route]

     ;; Set default guest content
     ::ihome/guest-content {:component ::default-guest-view :show-header? true}
     
     ::idev/plugins [{:id :homepage
                      :description "Home Page Implementation."
                      :responsibilities "Renders the dashboard, features grid, and metrics."
                      :type :infrastructure}]}

    :beans
    {::ihome/ui
     ^{:doc "Home page component implementation. Dependencies are injected via partial."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/create-home-page
      ::ihome/features
      ::ihome/metrics
      ::ihome/planning-action
      ::ihome/reports-action
      ::iauth/can?
      ::iauth/user
      ::ihome/guest-content]

     ::route
     ^{:doc "Route definition for home page"
       :api {:ret :map}}
     [core/make-route ::ihome/ui]

     ;; Default guest content implementation
     ::default-guest-view
     ^{:doc "Default view for unauthenticated users."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= core/default-guest-view]}}))
