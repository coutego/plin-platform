(ns plinpt.p-app-shell
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-sidebar :as isidebar]
            [plinpt.i-head-config :as ihead]
            [plinpt.i-session :as isession]
            [plinpt.i-app-shell :as iapp-shell]
            [plinpt.i-ui-components :as iui]
            [plinpt.i-router :as irouter]
            [plinpt.p-app-shell.core :as core]))

(def plugin
  (plin/plugin
    {:doc "PLIN Shell - A modern, professional layout with collapsible sidebar and clean design."
     :deps [boot/plugin iauth/plugin iapp/plugin idev/plugin ibread/plugin isidebar/plugin ihead/plugin isession/plugin iapp-shell/plugin iui/plugin irouter/plugin]
     
     :contributions
     {::idev/plugins [{:id :app-shell
                       :description "PLIN Shell Implementation."
                       :responsibilities "Provides a modern dashboard layout with collapsible sidebar, professional styling, and clean navigation."
                       :type :infrastructure}]
      
      ;; Override the application UI with our shell
      ::iapp/ui ::ui}

     :beans
     {;; The main shell component
      ::ui
      ^{:doc "PLIN Shell UI component. Dependencies are injected via partial."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [partial core/app-layout
       ::iapp/name
       ::iapp/short-description
       ::iapp/logo
       ::iapp/overlay-components
       ::isession/login-modal
       ::iauth/can?
       ::iauth/user
       ::ibread/trail-data
       ::ibread/trail-actions
       ::isidebar/ui
       ::isidebar/toggle!
       ::isidebar/collapsed?
       ::isession/user-data
       ::isession/user-actions
       ::ihead/inject!
       ::iui/error-boundary
       ::iapp/homepage
       ::iapp/structure
       ::irouter/setup!
       ::irouter/current-route]}}))
