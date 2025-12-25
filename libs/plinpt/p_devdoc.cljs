(ns plinpt.p-devdoc
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-homepage :as ihome]
            [plinpt.i-devtools :as itools]
            [plinpt.i-app-shell :as iapp]
            [plinpt.p-devdoc.core :as doc]))

(defn icon-doc []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]])

(defn create-doc-page [sections plugins]
  (fn []
    (let [default-section {:title "Application Design & Architecture"
                           :content (fn [] [doc/design-doc-view plugins])}
          has-section? (some #(= (:title %) (:title default-section)) sections)
          display-sections (if has-section? 
                             sections 
                             (cons default-section sections))]
      [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"}
       (for [{:keys [title content]} display-sections]
         [:div {:key title :class "mb-12"}
          [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} title]
          [content]])])))

(defn make-route [ui]
  {:path "/dev-doc" :component ui})

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing technical documentation pages, with architecture overview and development guidelines."
    :deps [idev/plugin ihome/plugin itools/plugin iapp/plugin]

    :contributions
    {;; Inject route into app shell
     ::iapp/routes [::route]

     ;; Inject into devtools
     ::itools/items [{:title "Architecture"
                      :description "Application design, plugin system, and architectural decisions."
                      :icon icon-doc
                      :color-class "bg-blue-600"
                      :href "/dev-doc"
                      :order 1}]
     
     ;; Register itself in the documentation
     ::idev/plugins [{:id :p-devdoc
                      :description "Implementation plugin providing technical documentation pages, with architecture overview and development guidelines."
                      :responsibilities "Core documentation data."
                      :type :infrastructure}]}

    :beans
    {::idev/ui
     ^{:doc "Main documentation page component implementation."
       :reagent-component true}
     [partial create-doc-page ::idev/sections ::idev/plugins]

     ::route
     ^{:doc "Route definition for the documentation page."}
     [make-route ::idev/ui]}}))
