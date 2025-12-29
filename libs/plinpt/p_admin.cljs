(ns plinpt.p-admin
  (:require [plin.core :as plin]
            [plinpt.i-admin :as i-admin]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-admin.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Plugin that implements an extensible admin interface."
    :deps [i-admin/plugin iapp/plugin idev/plugin]
    
    :contributions
    {::iapp/nav-items [::nav-item]
     
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
     
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [ui]
                      {:id :admin
                       :label "Admin"
                       :description "Administration tools and settings."
                       :route "/admin"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"}]]
                       :icon-color "text-purple-600 bg-purple-50"
                       :component ui
                       :order 100})
                    ::ui]}}}))
