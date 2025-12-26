(ns plinpt.p-head-config
  "Implementation plugin for dynamic head configuration.
   
   Injects scripts, styles, and Tailwind config into the document head
   based on contributions from other plugins."
  (:require [plin.core :as plin]
            [plinpt.i-head-config :as i-head]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-head-config.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation for dynamic head configuration injection."
    :deps [i-head/plugin idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :p-head-config
                      :description "Head Configuration Implementation."
                      :responsibilities "Injects dynamic scripts/styles into document head."
                      :type :infrastructure}]}
    
    :beans
    {::i-head/inject!
     ^{:doc "Injects all collected head resources into the DOM."
       :api {:args [] :ret :nil}}
     [core/create-injector
      ::i-head/scripts
      ::i-head/styles
      ::i-head/inline-styles
      ::i-head/tailwind-config
      ::i-head/meta-tags]}}))
