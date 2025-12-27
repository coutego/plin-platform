(ns plinpt.i-session
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
    {:doc "Interface plugin for session management, defining login modal, user data, user actions, and permission checking."
     :deps [idev/plugin]
     
     :contributions
     {::idev/plugins [{:id :i-session
                       :description "Interface for Session Management."
                       :responsibilities "Defines session state, user data, user actions, login modal, and user widget beans."
                       :type :infrastructure}]}

     :beans
     {::login-modal
      ^{:doc "Login modal component."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= nil]

      ::user-data
      ^{:doc "Reactive atom containing current user info for UI consumption.
              Derefs to: {:logged? bool, :name string, :initials string, :avatar-url string|nil, :roles set, :permissions set}"
        :api {:ret :atom}}
      [atom {:logged? false
             :name nil
             :initials nil
             :avatar-url nil
             :roles #{}
             :permissions #{}}]

      ::user-actions
      ^{:doc "Map of user-related actions/callbacks for UI components.
              Keys: :login! (opens login modal), :logout! (logs out user), :show-profile! (optional, navigates to profile)"
        :api {:ret :map}}
      [:= {:login! (fn [] (js/console.warn "No session implementation: login!"))
           :logout! (fn [] (js/console.warn "No session implementation: logout!"))
           :show-profile! (fn [] (js/console.warn "No session implementation: show-profile!"))}]

      ::user-widget
      ^{:doc "Default user widget component. Skins may use this directly or build their own using ::user-data and ::user-actions."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= (fn [] [:div "(Username)"])]

      ::state
      ^{:doc "Session state atom (internal, for backward compatibility)."
        :api {:ret :atom}}
      [atom {}]

      ::can?
      ^{:doc "Function to check if the current user has a permission."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [:= (fn [_] true)]}}))
