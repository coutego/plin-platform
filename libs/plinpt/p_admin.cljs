(ns plinpt.p-admin
  (:require [plin.core :as plin]
            [plinpt.i-admin :as i-admin]
            [plinpt.i-nav-bar :as i-nav-bar]
            [plinpt.i-app-shell :as i-app-shell]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-admin.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Plugin that implements an extensible admin interface."
    :deps [i-admin/plugin i-nav-bar/plugin i-app-shell/plugin idev/plugin]
    
    :contributions
    {;; Register route
     ::i-app-shell/routes [::route]

     ;; Register nav item
     ::i-nav-bar/items
     [{:label "Admin"
       :route "/admin"
       :order 100}]
     
     ;; Register documentation
     ::idev/plugins [{:id :admin
                      :description "Admin interface implementation."
                      :responsibilities "Provides the admin shell and layout for admin sections."
                      :type :infrastructure}]}

    :beans
    {::ui
     ^{:doc "Admin page component. Dependencies injected via partial."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/admin-page ::i-admin/sections]

     ::route
     ^{:doc "Admin page route"
       :api {:ret :map}}
     [core/make-route ::ui]}}))
