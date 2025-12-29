(ns plinpt.p-breadcrumb
  (:require [plin.core :as plin]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-breadcrumb.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of a structure-based breadcrumb trail."
    :deps [ibread/plugin iapp/plugin idev/plugin]

    :contributions
    {::idev/plugins [{:id :breadcrumb
                      :description "Breadcrumb Navigation."
                      :responsibilities "Derives breadcrumb trail from Application Tree and current URL."
                      :type :feature}]}

    :beans
    {;; -- Breadcrumb Trail Data --
     ::ibread/trail-data
     ^{:doc "Reactive atom containing the current breadcrumb trail."
       :api {:ret :atom}}
     [core/make-trail-data-atom ::iapp/structure]

     ;; -- Breadcrumb Trail Actions --
     ::ibread/trail-actions
     ^{:doc "Map of trail-related actions/callbacks."
       :api {:ret :map}}
     [core/make-trail-actions]

     ;; -- Default Breadcrumb UI --
     ::ibread/ui
     ^{:doc "Default breadcrumb component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/breadcrumb-initializer 
      ::ibread/trail-data 
      ::ibread/trail-actions]}}))
