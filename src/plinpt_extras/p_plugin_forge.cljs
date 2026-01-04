(ns plinpt-extras.p-plugin-forge
  "AI-powered plugin creator for the PLIN Platform."
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-application :as iapp]
            [plinpt-extras.p-plugin-forge.core :as core]
            [plinpt-extras.p-plugin-forge.ui.main :as main]
            [plinpt-extras.p-plugin-forge.ui.overlay :as overlay]))

(defn init-with-api! [sys-api]
  (core/set-sys-api! sys-api)
  (core/init!))

(def plugin
  (plin/plugin
   {:doc "Plugin Forge - AI-powered plugin creator with hot-loading capabilities."
    :deps [iapp/plugin boot/plugin]

    :contributions
    {::iapp/nav-items [::nav-item]
     ::iapp/overlay-components [::overlay]}

    :beans
    {::page
     ^{:doc "Main Plugin Forge page with tabbed interface."
       :reagent-component true}
     [(fn [sys-api]
        (init-with-api! sys-api)
        main/forge-page)
      ::boot/api]

     ::overlay
     ^{:doc "Draggable overlay for controlling hot-loaded plugins."
       :reagent-component true}
     [:= overlay/draggable-panel]

     ::nav-item
     {:constructor [(fn [page]
                      {:id :plugin-forge
                       :parent-id :development
                       :label "Plugin Forge"
                       :description "AI-powered plugin creator"
                       :route "plugin-forge"
                       :order 50
                       :component page
                       :icon [:svg {:xmlns "http://www.w3.org/2000/svg"
                                    :class "h-5 w-5"
                                    :fill "none"
                                    :viewBox "0 0 24 24"
                                    :stroke "currentColor"}
                              [:path {:stroke-linecap "round"
                                      :stroke-linejoin "round"
                                      :stroke-width "2"
                                      :d "M13 10V3L4 14h7v7l9-11h-7z"}]]})
                    ::page]}}}))
