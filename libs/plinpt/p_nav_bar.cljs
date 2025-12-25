(ns plinpt.p-nav-bar
  (:require [plin.core :as plin]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-nav-bar :as inav]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-nav-bar.core :as core]))

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing the navigation bar component and registering it in the app shell header."
    :deps [inav/plugin iauth/plugin iapp/plugin idev/plugin]

    :beans
    {::inav/ui
     ^{:doc "Main navigation bar component implementation."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/create-nav-bar
      ::inav/icon
      ::inav/title
      ::inav/items
      ::inav/user-widget
      ::iauth/can?
      ::iauth/user]}

    :contributions
    {::iapp/header-components [::inav/ui]
     ::idev/plugins [{:id :nav-bar
                      :description "Navigation Bar Implementation."
                      :responsibilities "Renders the top navigation bar."
                      :type :infrastructure}]}}))
