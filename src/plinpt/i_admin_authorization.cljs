(ns plinpt.i-admin-authorization
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Plugin for the authorisation."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-admin-authorization
                      :description "Interface for Admin Authorization."
                      :responsibilities "Defines beans for auth pages and tools."
                      :type :feature}]}

    :beans
    {::page
     ^{:doc "Main authorization management page component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= (fn [] [:div "Authorization Page Placeholder"])]

     ::route
     ^{:doc "Route definition for the authorization page."
       :api {:ret :map}}
     [:= {:path "/admin/authorization" :component (fn [] [:div "Auth"])}]

     ::debugger-component
     ^{:doc "Debug component to inspect current user permissions."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= (fn [] [:div "Auth Debugger"])]

     ::debugger-tool
     ^{:doc "Debug tool definition."
       :api {:ret :map}}
     [:= nil]}}))
