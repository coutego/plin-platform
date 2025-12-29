(ns plinpt.p-admin-authorization
  (:require [plin.core :as plin]
            [plinpt.i-admin-authorization :as iadmin-auth]
            [plinpt.i-session :as session]
            [plinpt.i-admin :as admin]
            [plinpt.i-ui-components :as ui]
            [plinpt.i-devtools :as devtools]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-service-authorization :as iservice]
            [plinpt.p-admin-authorization.core :as core]))

(def icon-lock
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"}]])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin for authorization management in the admin interface, providing pages, routes, and debug tools."
    :deps [session/plugin admin/plugin ui/plugin devtools/plugin iadmin-auth/plugin iservice/plugin iapp/plugin idev/plugin]

    :contributions
    {;; Authorization as child of Admin (Admin root is registered by p_admin.cljs)
     ::iapp/nav-items [::nav-item]

     ;; Register admin section (for admin page grid)
     ::admin/sections [{:id :authorization
                        :label "Authorization"
                        :description "Manage users and permissions."
                        :href "/admin/authorization"
                        :order 3
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
                    ::iadmin-auth/debugger-component]}
      
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [page]
                      {:id :authorization
                       :parent-id :admin
                       :label "Authorization"
                       :description "Manage users, groups, roles and permissions."
                       :route "authorization"
                       :icon icon-lock
                       :icon-color "text-red-600 bg-red-50"
                       :component page
                       :order 1
                       :required-perm :perm/admin})
                    ::iadmin-auth/page]}}}))
