(ns plinpt.i-tracer
  (:require [plin.core :as pi]))

(def plugin
  (pi/plugin
   {:beans
    {::api
     ^{:doc "The Tracing API. Contains functions to start/end traces and log SQL."
       :api {:ret :map}}
     [:= nil]

     ::ui
     ^{:doc "The Reagent component for the Tracer Dashboard."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= (fn [] [:div "Tracer UI not loaded"])]}}))
