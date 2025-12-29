(ns plinpt.p-app-shell
  (:require [plin.core :as plin]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-sidebar :as isidebar]
            [plinpt.i-head-config :as ihead]
            [plinpt.i-session :as isession]
            [plinpt.i-app-shell :as iapp-shell]
            [plinpt.p-app-shell.core :as core]))

(def plugin
  (plin/plugin
    {:doc "PLIN Shell - A modern, professional layout with collapsible sidebar and clean design."
     :deps [iauth/plugin iapp/plugin idev/plugin ibread/plugin isidebar/plugin ihead/plugin isession/plugin iapp-shell/plugin]
     
     :contributions
     {::idev/plugins [{:id :app-shell
                       :description "PLIN Shell Implementation."
                       :responsibilities "Provides a modern dashboard layout with collapsible sidebar, professional styling, and clean navigation."
                       :type :infrastructure}]}

     :beans
     {::iapp/ui
      ^{:doc "PLIN Shell UI component. Dependencies are injected via partial."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [partial core/app-layout
       ::iapp/name
       ::iapp/short-description
       ::iapp/logo
       ::iapp/all-routes
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
       ::ihead/inject!]
      
      ;; Override the p-app-shell mount bean so the boot mechanism picks it up
      :plinpt.p-app-shell/mount
      ^{:doc "Mounts the application to the DOM."
        :api {:args [["root-component" nil :component]] :ret :any}}
      [core/mount-app ::iapp/ui]}}))
