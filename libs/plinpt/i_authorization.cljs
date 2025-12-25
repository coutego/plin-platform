(ns plinpt.i-authorization
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
    {:doc "Interface plugin for authorization, defining functions for permission checking, user management, and reactive user state."
     :deps [idev/plugin]
     
     :contributions
     {::idev/plugins [{:id :i-authorization
                       :description "Interface for Authorization."
                       :responsibilities "Defines permission checking functions and user state."
                       :type :infrastructure}]}

     :beans
     {::get-permissions
      ^{:doc "Function to get all permissions for the current user."
        :api {:args [] :ret :vector}}
      [:= []]

      ::has-permission?
      ^{:doc "Function to check if the current user has a specific permission."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [partial (fn [_perm] false)]

      ::has-all?
      ^{:doc "Function to check if the current user has all of the specified permissions."
        :api {:args [["perms" [:perm/admin] :vector]] :ret :boolean}}
      [partial (fn [_perms] false)]

      ::has-any?
      ^{:doc "Function to check if a user has any of the specified permissions."
        :api {:args [["perms" [:perm/admin] :vector]] :ret :boolean}}
      [partial (fn [_perms] false)]

      ::set-user!
      ^{:doc "Function to set the current logged-in user."
        :api {:args [["user" nil :map]] :ret :any}}
      [partial (fn [_drop])]

      ::user
      ^{:doc "Reactive atom/track containing the current user's data."
        :api {:ret :atom}}
      [atom {}]

      ::can?
      ^{:doc "Function to check if the *current* user has a permission."
        :api {:args [["perm" :perm/admin :keyword]] :ret :boolean}}
      [partial (fn [_drop] false)]}}))
