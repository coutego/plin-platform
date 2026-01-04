(ns plinpt.p-js-utils
  "Implementation plugin for JavaScript utilities."
  (:require [plin.core :as plin]
            [plinpt.i-js-utils :as ijs]
            [plinpt.i-router :as irouter]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-js-utils.core :as core]))

(def plugin
  (plin/plugin
   {:doc "JavaScript utilities implementation. Provides helpers for JS plugin developers."
    :deps [ijs/plugin irouter/plugin idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :p-js-utils
                      :description "JavaScript Utilities Implementation."
                      :responsibilities "Provides atom helpers, data access utilities, and React hooks for JS plugins."
                      :type :infrastructure}]}
    
    :beans
    {::ijs/api
     ^{:doc "JavaScript utilities API implementation."
       :api {:ret :object}}
     [core/create-api ::irouter/navigate! ::irouter/current-route]}}))
