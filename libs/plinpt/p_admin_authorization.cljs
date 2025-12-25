(ns plinpt.p-admin-authorization
  (:require [plin.core :as plin]
            [plinpt.i-admin-authorization :as iadmin-auth]
            [plinpt.i-session :as session]
            [plinpt.i-admin :as admin]
            [plinpt.i-ui-components :as ui]
            [plinpt.i-devtools :as devtools]
            [plinpt.i-app-shell :as app-shell]
            [plinpt.i-homepage :as homepage]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-service-authorization :as iservice]
            [plinpt.p-admin-authorization.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin for authorization management in the admin interface, providing pages, routes, and debug tools."
    :deps [session/plugin admin/plugin ui/plugin devtools/plugin iadmin-auth/plugin homepage/plugin iservice/plugin app-shell/plugin idev/plugin]

    :contributions
    {;; Register route
     ::app-shell/routes [::iadmin-auth/route]

     ;; Register admin section
     ::admin/sections [{:id :authorization
                        :label "Authorization"
                        :description "Manage users and permissions."
                        :href "/admin/authorization"
                        :order 3
                        :required-perm :perm/admin}]

     ;; Register home feature
     ::homepage/features [{:title "Authorization"
                           :description "Manage users, groups, roles and permissions."
                           :icon core/icon-lock
                           :color-class "bg-red-500"
                           :href "/admin/authorization"
                           :order 10
                           :required-perm :perm/admin}]

     ::idev/plugins [{:id :admin-authorization
                      :description "Admin Authorization Feature."
                      :responsibilities "Provides admin pages for managing users and permissions."
                      :type :feature}]}

    :beans
    {::iadmin-auth/page
     ^{:doc "Main authorization management page.
      Parameters: None (dependencies injected via closure)."
       :reagent-component true}
     [partial core/auth-page
      ::session/can?
      ::iservice/user-service
      ::iservice/group-service
      ::iservice/role-service
      ::iservice/permission-service
      ::ui/list-page
      ::ui/data-table
      ::ui/icon-add
      ::ui/tabs]

     ::iadmin-auth/route
     ^{:doc "Route definition for the authorization page.
      Parameters: None (returns a route map)."
       :reagent-component false}
     {:constructor [(fn [page] {:path "/admin/authorization" :component page})
                    ::iadmin-auth/page]}

     ::iadmin-auth/debugger-component
     ^{:doc "Debug component to inspect current user permissions.
      Parameters: None (dependencies injected via closure)."
       :reagent-component true}
     [partial core/permission-debugger
      ::session/state]

     ::iadmin-auth/debugger-tool
     ^{:doc "Debug tool definition for the authorization debugger.
      Parameters: None (returns a tool map)."
       :reagent-component false}
     {:constructor [(fn [comp]
                      {:title "User Permissions"
                       :description "Inspect current user roles and permissions."
                       :component comp})
                    ::iadmin-auth/debugger-component]}}}))
