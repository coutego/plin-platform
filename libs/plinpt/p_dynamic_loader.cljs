(ns plinpt.p-dynamic-loader
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-dynamic-loader :as i-loader]
            [plinpt.i-app-shell :as app-shell]
            [plinpt.i-devtools :as devtools]
            [plinpt.p-dynamic-loader.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of Dynamic Plugin Loader."
    :deps [i-loader/plugin app-shell/plugin devtools/plugin boot/plugin]
    
    :contributions
    {;; We contribute the ::route bean.
     ;; We wrap it in a vector because the extension handler (collect-all) expects a sequence of items.
     ::app-shell/routes [::route]
     
     ::devtools/items [{:title "Dynamic Loader"
                        :description "Load .cljs plugin files at runtime."
                        :icon core/icon-upload
                        :color-class "bg-green-600"
                        :href "/development/loader"
                        :order 100}]}

    :beans
    {;; We define a bean for the route so we can inject the boot API.
     ;; The route component needs the API to register new plugins.
     ::route
     ^{:doc "Route for the dynamic loader, with dependencies injected."
       :api {:args [["sys-api" {} :map]] :ret :map}}
     [(fn [sys-api handlers]
        {:path "/development/loader"
         ;; We use partial to inject the API into the UI component
         :component (partial core/loader-page sys-api handlers)})
      ::boot/api
      ::i-loader/handlers]}}))
