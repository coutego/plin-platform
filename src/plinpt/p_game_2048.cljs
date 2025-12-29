(ns plinpt.p-game-2048
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-game-2048.core :as core]))

(def icon-game
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M14.828 14.828a4 4 0 01-5.656 0M9 10h.01M15 10h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing a 2048 game for developer tools, with keyboard controls and reactive state."
    :deps [iapp/plugin idev/plugin]

    :contributions
    {;; Register Extra section in sidebar (root level)
     ::iapp/nav-items [::nav-extra ::nav-game]
     
     ::idev/plugins [{:id :game-2048
                      :description "2048 Game."
                      :responsibilities "Provides a game for the extra section."
                      :type :feature}]}

    :beans
    {::ui
     ^{:doc "The 2048 game component"
       :reagent-component true}
     [:= core/game-board]
     
     ::nav-extra
     [:= {:id :extra
          :label "Extra"
          :description "Games and other extras."
          :route "/extra"
          :icon icon-game
          :icon-color "text-orange-600 bg-orange-50"
          :order 300}]
     
     ::nav-game
     {:constructor [(fn [ui]
                      {:id :game-2048
                       :parent-id :extra
                       :label "2048 Game"
                       :description "Play the classic 2048 game."
                       :route "2048"
                       :icon icon-game
                       :icon-color "text-orange-600 bg-orange-50"
                       :component ui
                       :order 10})
                    ::ui]}}}))
