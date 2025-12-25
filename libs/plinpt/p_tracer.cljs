(ns plinpt.p-tracer
  (:require [plin.core :as pi]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.p-app-shell :as app]
            [plinpt.i-app-shell :as iapp]
            [plinpt.p-tracer.core :as core]
            [plinpt.p-tracer.ui :as ui]))

(def plugin
  (pi/plugin
   {:deps [i-tracer/plugin app/plugin iapp/plugin]
    :contributions
    {::iapp/routes [::route]}
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
     
     ::route
     ^{:doc "Route definition for the tracer dashboard."
       :api {:ret :map}}
     [ui/make-route ::i-tracer/ui]}}))
