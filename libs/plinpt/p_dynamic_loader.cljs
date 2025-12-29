(ns plinpt.p-dynamic-loader
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-dynamic-loader :as i-loader]
            [plinpt.i-application :as iapp]
            [plinpt.p-dynamic-loader.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of Dynamic Plugin Loader."
    :deps [i-loader/plugin iapp/plugin boot/plugin]
    
    :contributions
    {::iapp/nav-items [::nav-item]}

    :beans
    {::ui
     ^{:doc "Dynamic loader page component."
       :reagent-component true}
     [(fn [sys-api handlers]
        (partial core/loader-page sys-api handlers))
      ::boot/api
      ::i-loader/handlers]
     
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [ui]
                      {:id :loader
                       :parent-id :development
                       :label "Dynamic Loader"
                       :description "Load plugins dynamically at runtime."
                       :route "loader"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12"}]]
                       :icon-color "text-cyan-600 bg-cyan-50"
                       :component ui
                       :order 100})
                    ::ui]}}}))
