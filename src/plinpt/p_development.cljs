(ns plinpt.p-development
  (:require [plin.core :as plin]
            [plinpt.i-development :as idev]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idevdoc]
            [plinpt.p-development.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing development documentation page and integration with devtools and devdoc sections."
    :deps [idev/plugin iapp/plugin idevdoc/plugin]

    :contributions
    {::iapp/nav-items [::nav-item]

     ;; Inject into dev-doc
     ::idevdoc/sections [{:title "Development Guidelines"
                          :content (fn []
                                     [:div {:class "space-y-4"}
                                      [:p "This section contains development guidelines and best practices for working with the plugin system."]
                                      [:p "Refer to the main technical documentation for detailed architecture information."]])}]
     
     ::idevdoc/plugins [{:id :development
                         :description "Development Documentation."
                         :responsibilities "Provides development guidelines and resources."
                         :type :infrastructure}]}

    :beans
    {::idev/ui
     ^{:doc "Development documentation page implementation."
       :reagent-component true}
     [partial core/create-development-page]
     
     ::ui
     ^{:doc "Development docs page for nav"
       :reagent-component true}
     [partial core/create-development-page]
     
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [ui]
                      {:id :development-docs
                       :parent-id :development
                       :label "Documentation"
                       :description "Development guidelines and best practices."
                       :route "docs"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"}]]
                       :icon-color "text-blue-600 bg-blue-50"
                       :component ui
                       :order 1})
                    ::idev/ui]}}}))
