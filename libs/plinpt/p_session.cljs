(ns plinpt.p-session
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-session :as isession]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-nav-bar :as inav]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-service-authorization :as iservice]
            [plinpt.i-service-invoker :as invoker]
            [plinpt.p-session.core :as core]
            [plinpt.p-session.utils :as utils]))

(def plugin
  (plin/plugin
    {:doc "Implementation plugin providing session management, user authentication, and UI components for login and user widget."
     :deps [isession/plugin iauth/plugin inav/plugin iapp/plugin iservice/plugin invoker/plugin idev/plugin]

     :beans
     {;; -- Authorization Implementation --
      ::iauth/user
      ^{:doc "Reactive track containing the current user's data."
        :api {:ret :atom}}
      [(fn [] (r/track core/get-current-user))]

      ::iauth/set-user!
      ^{:doc "Function to set the current logged-in user."
        :api {:args [["user" nil :map]] :ret :any}}
      [:= core/set-user!]

      ::iauth/can?
      ^{:doc "Function to check if the current user has a permission."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [:= core/can?]

      ::iauth/get-permissions
      ^{:doc "Function to get all permissions for the current user."
        :api {:args [] :ret :vector}}
      [:= core/get-permissions]

      ::iauth/has-permission?
      ^{:doc "Alias for can?."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [:= core/has-permission?]

      ::iauth/has-all?
      ^{:doc "Check if user has all specified permissions."
        :api {:args [["perms" [:perm/admin] :vector]] :ret :boolean}}
      [:= core/has-all?]

      ::iauth/has-any?
      ^{:doc "Check if user has any of the specified permissions."
        :api {:args [["perms" [:perm/admin] :vector]] :ret :boolean}}
      [:= core/has-any?]

      ;; -- Session UI Implementation --
      ::isession/login-modal
      ^{:doc "Login modal component. Dependency injected via partial."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [partial (fn [user-service] (fn [] [core/login-modal user-service])) ::iservice/user-service]

      ::isession/user-widget
      ^{:doc "User widget component for the navigation bar."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= core/user-widget]

      ::isession/state
      ^{:doc "Session state atom."
        :api {:ret :atom}}
      [:= core/session-state]

      ::isession/can?
      ^{:doc "Permission check function."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [:= core/can?]
      
      ;; -- Service Implementation (CRUD) --
      ::iservice/user-service
      ^{:doc "Service for managing users." :api {:ret :map}}
      [(fn [inv] (utils/make-service inv :users)) ::invoker/invoke]

      ::iservice/group-service
      ^{:doc "Service for managing groups." :api {:ret :map}}
      [(fn [inv] (utils/make-service inv :groups)) ::invoker/invoke]

      ::iservice/role-service
      ^{:doc "Service for managing roles." :api {:ret :map}}
      [(fn [inv] (utils/make-service inv :roles)) ::invoker/invoke]

      ::iservice/permission-service
      ^{:doc "Service for managing permissions." :api {:ret :map}}
      [(fn [inv] (utils/make-service inv :permissions)) ::invoker/invoke]}

     :contributions
     {;; Register UI
      ::inav/user-widget [identity ::isession/user-widget]

      ;; Mount the login modal via header-components
      ::iapp/header-components [identity ::isession/login-modal]
      
      ::idev/plugins [{:id :session
                       :description "Session Management & Auth Services."
                       :responsibilities "Handles user login, session state, permission logic, and CRUD services for auth entities."
                       :type :infrastructure}]}}))
