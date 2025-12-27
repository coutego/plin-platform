(ns plinpt.p-breadcrumb
  (:require [plin.core :as plin]
            [plinpt.i-breadcrumb :as ibread]
            [plinpt.i-app-shell :as iapp]
            [plinpt.i-devdoc :as idev]
            [plinpt.p-breadcrumb.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation of a history-based breadcrumb trail with skinnable data/actions pattern."
    :deps [ibread/plugin iapp/plugin idev/plugin]

    :contributions
    {::idev/plugins [{:id :breadcrumb
                      :description "Breadcrumb Navigation."
                      :responsibilities "Tracks navigation history and renders breadcrumbs. Exposes trail-data and trail-actions for custom skins."
                      :type :feature}]}

    :beans
    {;; -- Breadcrumb Trail Data (reactive, for UI consumption) --
     ::ibread/trail-data
     ^{:doc "Reactive atom containing the current breadcrumb trail."
       :api {:ret :atom}}
     [core/make-trail-data-atom]

     ;; -- Breadcrumb Trail Actions (callbacks for UI) --
     ::ibread/trail-actions
     ^{:doc "Map of trail-related actions/callbacks."
       :api {:ret :map}}
     [core/make-trail-actions ::iapp/routes]

     ;; -- Default Breadcrumb UI --
     ::ibread/ui
     ^{:doc "Default breadcrumb component using ::trail-data and ::trail-actions."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial core/breadcrumb-initializer 
      ::iapp/routes 
      ::ibread/trail-data 
      ::ibread/trail-actions]}}))
