(ns plinpt.p-tracer
  (:require [plin.core :as pi]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.p-app-shell :as app]
            [plinpt.i-application :as iapp]
            [plinpt.p-tracer.core :as core]
            [plinpt.p-tracer.ui :as ui]))

(def plugin
  (pi/plugin
   {:deps [i-tracer/plugin app/plugin iapp/plugin]
    :contributions
    {::iapp/nav-items [::nav-item]}
    :beans
    {::i-tracer/api
     ^{:doc "API with decorators for tracing service calls and SQL."
       :api {:ret :map}}
     [:= {:wrap-invoker core/wrap-invoker
          :wrap-executor core/wrap-executor}]

     ::i-tracer/ui
     ^{:doc "The Reagent component for the Tracer Dashboard."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= ui/tracer-dashboard]
     
     ::ui
     ^{:doc "Tracer page wrapper"
       :reagent-component true}
     [:= ui/tracer-dashboard]
     
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [ui]
                      {:id :tracer
                       :parent-id :development
                       :label "Service Tracer"
                       :description "Trace service calls and SQL execution."
                       :route "tracer"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 10V3L4 14h7v7l9-11h-7z"}]]
                       :icon-color "text-amber-600 bg-amber-50"
                       :component ui
                       :order 10})
                    ::ui]}}}))
