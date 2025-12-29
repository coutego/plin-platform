(ns plinpt.p-client-boot
  "Client-side boot plugin.
   
   Provides the ::boot/boot-fn implementation that mounts the root Reagent
   component to the DOM."
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-application :as iapp]
            [reagent.dom :as rdom]))

;; --- Boot Function ---

(defn mount-app!
  "Mounts the root component to the DOM element with id 'app'.
   This is the ::boot/boot-fn implementation for browser environments."
  [root-component _container]
  (if root-component
    (do
      (js/console.log "Mounting application to #app...")
      (rdom/render [root-component] (.getElementById js/document "app")))
    (js/console.warn "No root component provided. Nothing to mount.")))

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Client-side boot plugin. Mounts the root Reagent component to the DOM."
    :deps [boot/plugin iapp/plugin]
    
    :beans
    {::boot/boot-fn
     ^{:doc "Boot function that mounts the UI to the DOM."
       :api {:args [["container" {} :map]] :ret :nil}}
     {:constructor [(fn [root-component]
                      (fn [container]
                        (mount-app! root-component container)))
                    ::iapp/ui]}}}))
