(ns plinpt.i-nav-bar
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for navigation bar, allowing contributions of menu items and user widget components."
    :deps [idev/plugin]
    
    :contributions
    {::title "PLIN Management App"
     ::icon "PL"
     ::idev/plugins [{:id :i-nav-bar
                      :description "Interface for the Navigation Bar."
                      :responsibilities "Defines extension points for menu items and user widget."
                      :type :infrastructure}]}

    :extensions
    [{:key ::icon
      :handler (plin/collect-last ::icon)
      :doc "Application icon (component or string)"}
     {:key ::title
      :handler (plin/collect-last ::title)
      :doc "Application title (string)"}
     {:key ::items
      :handler (plin/collect-data ::items)
      :doc "Menu links {:label \"String\" :route \"/target\" :order int :required-perm keyword}"}
     {:key ::user-widget
      :handler (plin/collect-last ::user-widget)
      :doc "User profile widget component"}]

    :beans
    {::ui
     ^{:doc "Main navigation bar component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= [:div "Nav Bar"]]

     ::items
     ^{:doc "List of navigation items."
       :api {:ret :vector}}
     [:= []]

     ::user-widget
     ^{:doc "User widget component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]}}))
