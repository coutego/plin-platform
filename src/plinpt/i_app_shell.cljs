(ns plinpt.i-app-shell
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
    {:doc "Interface plugin for the application shell, providing header and overlay extension points."
     :deps [idev/plugin]
     
     :contributions
     {::idev/plugins [{:id :i-app-shell
                       :description "Interface for Application Shell."
                       :responsibilities "Defines extension points for headers and overlays."
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
     {;; UI bean kept for backward compatibility and as a logical grouping point
      ;; Shells should override ::boot/root-component instead
      ::ui 
      ^{:doc "Main App Shell UI component. 
              Note: This is kept for organizational purposes. 
              The actual root component should be set via ::plin.boot/root-component."
        :reagent-component true
        :api {:args [] :ret :hiccup}}
      [:= (fn [] [:div "No shell loaded. Please include an app shell plugin."])]
      
      ::overlay-components 
      ^{:doc "List of overlay components."
        :api {:ret :vector}}
      [:= []]
      
      ::header-components
      ^{:doc "List of header components."
        :api {:ret :vector}}
      [:= []]}}))
