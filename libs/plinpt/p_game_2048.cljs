(ns plinpt.p-game-2048
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-app-shell :as app-shell]
            [plinpt.i-devtools :as devtools]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-game-2048.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing a 2048 game for developer tools, with keyboard controls and reactive state."
    :deps [app-shell/plugin devtools/plugin idev/plugin]

    :contributions
    {::app-shell/routes [::route]

     ::devtools/items
     [{:title "2048"
       :description "Relax with a game of 2048."
       :icon core/icon-game
       :color-class "bg-yellow-500"
       :href "/development/2048"
       :order 100}]
     
     ::idev/plugins [{:id :game-2048
                      :description "2048 Game."
                      :responsibilities "Provides a game for the developer tools section."
                      :type :feature}]}

    :beans
    {::ui
     ^{:doc "The 2048 game component"
       :reagent-component true}
     [:= core/game-board]

     ::route
     ^{:doc "Route for 2048 game"}
     [(fn [ui] {:path "/development/2048" :component ui})
      ::ui]}}))
