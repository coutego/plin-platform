(ns plinpt.i-app-shell
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
    {:doc "Interface plugin for the application shell, providing routing, header, and overlay extension points."
     :deps [idev/plugin]
     
     :contributions
     {::idev/plugins [{:id :i-app-shell
                       :description "Interface for Application Shell."
                       :responsibilities "Defines extension points for routes, headers, and overlays."
                       :type :infrastructure}]}

     :extensions
     [{:key ::routes
       :doc "List of route maps {:path \"/url\" :component reagent-component :layout :default|:full-screen :required-perm permission}"
       :handler (plin/collect-all ::routes)}

      {:key ::header-components
       :doc "Components to be rendered in the global header"
       :handler (plin/collect-all ::header-components)}

      {:key ::overlay-components
       :doc "Components to be rendered as global overlays (e.g. notifications, modals)"
       :handler (plin/collect-all ::overlay-components)}]

     :beans
     {;; Default values
      ::ui 
      ^{:doc "Main App Shell UI component."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= [:div "404 Page not found"]]
      
      ::overlay-components 
      ^{:doc "List of overlay components."
        :api {:ret :vector}}
      [:= []]
      
      ::routes 
      ^{:doc "List of route definitions."
        :api {:ret :vector}}
      [:= []]
      
      ::header-components 
      ^{:doc "List of header components."
        :api {:ret :vector}}
      [:= []]}}))
