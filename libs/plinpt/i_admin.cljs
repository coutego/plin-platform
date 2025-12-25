(ns plinpt.i-admin
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for an application admin interface that alows to register 'sections'."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-admin
                      :description "Interface for Admin."
                      :responsibilities "Defines extension points for admin sections."
                      :type :infrastructure}]}

    :extensions
    [{:key ::sections
      :handler (plin/collect-data ::sections)
      :doc "List of admin sections. Schema: {:id keyword :label string :description string :href string :order int :required-perm keyword}"}]

    :beans
    {::sections
     ^{:doc "List of registered admin sections."
       :api {:ret :vector}}
     [:= []]}}))
