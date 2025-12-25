(ns plinpt.p-app-shell
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.p-app-shell.doc :as doc]
            [plinpt.p-app-shell.core :as core]))


(def plugin
  (plin/plugin
    {:doc "Implementation plugin providing the main application shell with routing, layout, error boundaries, and mounting logic."
     :deps [iauth/plugin iapp/plugin idev/plugin ibread/plugin]
     
     :contributions
     {::idev/plugins [{:id :app-shell
                       :description "Application Shell Implementation."
                       :responsibilities "Handles routing, layout, global headers, and error boundaries."
                       :type :infrastructure
                       :doc-functional doc/functional
                       :doc-technical doc/technical}]}

     :beans
     {::iapp/ui
      ^{:doc "Main App Shell UI component. Dependencies are injected via partial."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [partial core/app-layout
       ::iapp/header-components
       ::iapp/routes
       ::iapp/overlay-components
       ::iauth/can?
       ::iauth/user
       ::ibread/ui]
      
      ::mount
      ^{:doc "Mounts the application to the DOM."
        :api {:args [["root-component" nil :component]] :ret :any}}
      [core/mount-app ::iapp/ui]}}))
