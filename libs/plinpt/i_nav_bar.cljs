(ns plinpt.i-nav-bar
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for navigation bar, allowing contributions of menu items, user widget, and user data/actions for custom skins."
    :deps [idev/plugin]
    
    :contributions
    {::title "PLIN Management App"
     ::icon "PL"
     ::idev/plugins [{:id :i-nav-bar
                      :description "Interface for the Navigation Bar."
                      :responsibilities "Defines extension points for menu items, user widget, and user data/actions."
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
      :doc "User profile widget component (default implementation)"}
     {:key ::user-data
      :handler (plin/collect-last ::user-data)
      :doc "Reactive atom with user info for custom skins: {:logged? :name :initials :avatar-url :roles :permissions}"}
     {:key ::user-actions
      :handler (plin/collect-last ::user-actions)
      :doc "Map of user actions for custom skins: {:login! :logout! :show-profile!}"}]

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
     ^{:doc "User widget component (default implementation)."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]

     ::user-data
     ^{:doc "Reactive atom with user info for custom skins."
       :api {:ret :atom}}
     [atom {:logged? false
            :name nil
            :initials nil
            :avatar-url nil
            :roles #{}
            :permissions #{}}]

     ::user-actions
     ^{:doc "Map of user actions for custom skins."
       :api {:ret :map}}
     [:= {:login! (fn [] (js/console.warn "No session implementation: login!"))
          :logout! (fn [] (js/console.warn "No session implementation: logout!"))
          :show-profile! (fn [] (js/console.warn "No session implementation: show-profile!"))}]}}))
