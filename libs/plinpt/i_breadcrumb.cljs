(ns plinpt.i-breadcrumb
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for Breadcrumb navigation."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-breadcrumb
                      :description "Interface for Breadcrumbs."
                      :responsibilities "Defines the UI bean for the breadcrumb trail, plus lower-level data and actions for custom skins."
                      :type :infrastructure}]}

    :beans
    {::trail-data
     ^{:doc "Reactive atom containing the current breadcrumb trail.
             Derefs to: {:trail [{:path string, :label string} ...]}"
       :api {:ret :atom}}
     [atom {:trail []}]

     ::trail-actions
     ^{:doc "Map of breadcrumb-related actions/callbacks for UI components.
             Keys: :navigate! (fn [path] - navigates and updates trail), 
                   :clear! (fn [] - resets trail to root),
                   :go-back! (fn [] - navigates to previous item in trail)"
       :api {:ret :map}}
     [:= {:navigate! (fn [_] (js/console.warn "No breadcrumb implementation: navigate!"))
          :clear! (fn [] (js/console.warn "No breadcrumb implementation: clear!"))
          :go-back! (fn [] (js/console.warn "No breadcrumb implementation: go-back!"))}]

     ::ui
     ^{:doc "The default Breadcrumb UI component. Skins may use this directly or build their own using ::trail-data and ::trail-actions."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= nil]}}))
