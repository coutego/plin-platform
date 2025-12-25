(ns plinpt.p-debug
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-devtools :as idev]
            [plinpt.i-app-shell :as app-shell]
            [plinpt.i-devdoc :as idev-doc]
            [plinpt.p-debug.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin for System Debugger."
    :deps [idev/plugin app-shell/plugin idev-doc/plugin boot/plugin]
    
    :contributions
    {;; Add to DevTools items
     ::idev/items [{:title "System Debugger"
                    :description "Inspect loaded plugins, beans, and system state."
                    :icon core/icon-debug
                    :href "/debug"
                    :color-class "bg-purple-500"
                    :order 10}]
     
     ;; Register Route
     ::app-shell/routes [::route]
     
     ::idev-doc/plugins [{:id :debug
                          :description "System Debugger."
                          :responsibilities "Provides introspection into the plugin system."
                          :type :tool}]}
    
    :beans
    {::ui 
     ^{:doc "The main debug UI component"
       :reagent-component true}
     [partial core/debug-panel ::boot/api]
     
     ::route
     ^{:doc "Route for the debug page"}
     [core/make-route ::ui]}}))
