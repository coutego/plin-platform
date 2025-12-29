(ns plinpt.p-devdoc
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-homepage :as ihome]
            [plinpt.i-application :as iapp]
            [plinpt.p-devdoc.core :as doc]))

(defn create-doc-page [sections plugins]
  [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"}
   (let [default-section {:title "Application Design & Architecture"
                          :content (fn [] [doc/design-doc-view plugins])}
         has-section? (some #(= (:title %) (:title default-section)) sections)
         display-sections (if has-section? 
                            sections 
                            (cons default-section sections))]
     (for [{:keys [title content]} display-sections]
       ^{:key title}
       [:div {:class "mb-12"}
        [:h2 {:class "text-2xl font-semibold text-gray-900 mb-4"} title]
        [content]]))])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing technical documentation pages, with architecture overview and development guidelines."
    :deps [idev/plugin ihome/plugin iapp/plugin]

    :contributions
    {::iapp/nav-items [::nav-item]
     
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
     
     ::nav-item
     ^{:doc "Nav item with injected component"}
     {:constructor [(fn [ui]
                      {:id :dev-doc
                       :parent-id :development
                       :label "Architecture"
                       :description "Application design and architecture overview."
                       :route "architecture"
                       :icon [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                              [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
                                      :d "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"}]]
                       :icon-color "text-indigo-600 bg-indigo-50"
                       :component ui
                       :order 5})
                    ::idev/ui]}}}))
