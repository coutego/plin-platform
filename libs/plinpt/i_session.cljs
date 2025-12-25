(ns plinpt.i-session
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
    {:doc "Interface plugin for session management, defining login modal, user widget, session state, and permission checking."
     :deps [idev/plugin]
     
     :contributions
     {::idev/plugins [{:id :i-session
                       :description "Interface for Session Management."
                       :responsibilities "Defines session state, login modal, and user widget beans."
                       :type :infrastructure}]}

     :beans
     {::login-modal
      ^{:doc "Login modal component."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= nil]

      ::user-widget
      ^{:doc "User widget component."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= [:div "(Username)"]]

      ::state
      ^{:doc "Session state atom."
        :api {:ret :atom}}
      [atom {}]

      ::can?
      ^{:doc "Function to check if the current user has a permission."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [:= (fn [_] true)]}}))
