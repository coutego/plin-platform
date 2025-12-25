(ns plinpt.i-devtools
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for developer tools, allowing contributions of tool items and defining the dev tools page."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-devtools
                      :description "Interface for Developer Tools."
                      :responsibilities "Defines extension points for dev tools items."
                      :type :infrastructure}]}

    :extensions
    [{:key ::items
      :handler (plin/collect-data ::items)
      :doc "List of developer tools. Schema: {:title string :description string :icon component :href string :color-class string :order int}"}]

    :beans
    {::ui
     ^{:doc "Developer tools page component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= [:div "Dev Tools"]]

     ::items
     ^{:doc "List of developer tools items."
       :api {:ret :vector}}
     [:= []]}}))
