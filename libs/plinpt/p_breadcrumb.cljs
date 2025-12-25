(ns plinpt.p-breadcrumb
  (:require [plin.core :as plin]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-breadcrumb.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of a history-based breadcrumb trail."
    :deps [ibread/plugin iapp/plugin idev/plugin]

    :contributions
    {::idev/plugins [{:id :breadcrumb
                      :description "Breadcrumb Navigation."
                      :responsibilities "Tracks navigation history and renders breadcrumbs."
                      :type :feature}]}

    :beans
    {::ibread/ui
     ^{:doc "Breadcrumb component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/breadcrumb-view ::iapp/routes]}}))
