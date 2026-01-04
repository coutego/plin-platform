(ns plinpt.p-debug
  (:require [plin.core :as plin]
            [plin.boot :as boot]
            [plinpt.i-application :as iapp]
            [plinpt.i-devdoc :as idev-doc]
            [plinpt.p-debug.core :as core]))

(def icon-plugins
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]])

(def icon-graph
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M7 21a4 4 0 01-4-4V5a2 2 0 012-2h4a2 2 0 012 2v12a4 4 0 01-4 4zm0 0h12a2 2 0 002-2v-4a2 2 0 00-2-2h-2.343M11 7.343l1.657-1.657a2 2 0 012.828 0l2.829 2.829a2 2 0 010 2.828l-8.486 8.485M7 17h.01"}]])

(def icon-beans
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"}]])

(def icon-debug
  [:svg {:class "h-5 w-5 sidebar-icon" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]])

(def plugin
  (plin/plugin
   {:doc "Implementation plugin for System Debugger."
    :deps [iapp/plugin idev-doc/plugin boot/plugin]
    
    :contributions
    {::iapp/nav-items [::nav-sys-debug
                       ::nav-plugins-manage
                       ::nav-plugins-graph
                       ::nav-beans-inspect
                       ::nav-beans-graph
                       ::nav-plugin-detail]
     
     ::idev-doc/plugins [{:id :debug
                          :description "System Debugger."
                          :responsibilities "Provides introspection into the plugin system."
                          :type :tool}]}
    
    :beans
    {;; Landing page for System Debugger
     ::ui 
     ^{:doc "The main debug landing page"
       :reagent-component true}
     [partial core/debug-panel ::boot/api]
     
     ;; Individual views as separate beans
     ::plugins-manage-ui
     ^{:doc "Plugin management view"
       :reagent-component true}
     [partial core/plugins-manage-view ::boot/api]
     
     ::plugins-graph-ui
     ^{:doc "Plugin dependency graph view"
       :reagent-component true}
     [partial core/plugins-graph-view ::boot/api]
     
     ::beans-inspect-ui
     ^{:doc "Bean inspector view"
       :reagent-component true}
     [partial core/beans-inspect-view ::boot/api]
     
     ::beans-graph-ui
     ^{:doc "Bean dependency graph view"
       :reagent-component true}
     [partial core/beans-graph-view ::boot/api]
     
     ;; Plugin detail page (with route parameter)
     ::plugin-detail-ui
     ^{:doc "Plugin detail page showing full plugin information"
       :reagent-component true}
     [partial core/plugin-detail-view ::boot/api]
     
     ;; Navigation Items (Split to avoid nested vectors in contributions)
     
     ::nav-sys-debug
     [:= {:id :sys-debug
          :parent-id :development
          :label "System Debugger"
          :description "Introspect plugins and beans."
          :route "debugger"
          :icon icon-debug
          :icon-color "text-rose-600 bg-rose-50"
          :order 50}]
     
     ::nav-plugins-manage
     {:constructor [(fn [ui]
                      {:id :debug-plugins-manage
                       :parent-id :sys-debug
                       :label "Manage Plugins"
                       :description "View and manage loaded plugins."
                       :route "plugins"
                       :icon icon-plugins
                       :icon-color "text-violet-600 bg-violet-50"
                       :component ui
                       :section "Plugins"
                       :order 1})
                    ::plugins-manage-ui]}
     
     ::nav-plugins-graph
     {:constructor [(fn [ui]
                      {:id :debug-plugins-graph
                       :parent-id :sys-debug
                       :label "Dependency Graph"
                       :description "Visualize plugin dependencies."
                       :route "plugin-graph"
                       :icon icon-graph
                       :icon-color "text-violet-600 bg-violet-50"
                       :component ui
                       :section "Plugins"
                       :order 2})
                    ::plugins-graph-ui]}
     
     ;; Plugin detail route - hidden from navigation, accessed only via links
     ;; Uses :id parameter - the plugin ID is encoded with ~ instead of /
     ::nav-plugin-detail
     {:constructor [(fn [ui]
                      {:id :debug-plugin-detail
                       :parent-id :sys-debug
                       :label "Plugin Detail"
                       :route "plugin/:id"
                       :hidden true  ;; Hide from sidebar and auto-generated parent pages
                       :component (fn [{:keys [id]}]
                                    ;; id comes from route param, already has ~ encoding
                                    [ui (or id "")])
                       :order 999})
                    ::plugin-detail-ui]}
     
     ::nav-beans-inspect
     {:constructor [(fn [ui]
                      {:id :debug-beans-inspect
                       :parent-id :sys-debug
                       :label "Inspector"
                       :description "Inspect bean values and metadata."
                       :route "beans"
                       :icon icon-beans
                       :icon-color "text-fuchsia-600 bg-fuchsia-50"
                       :component ui
                       :section "Beans (Container)"
                       :order 10})
                    ::beans-inspect-ui]}
     
     ::nav-beans-graph
     {:constructor [(fn [ui]
                      {:id :debug-beans-graph
                       :parent-id :sys-debug
                       :label "Dependency Graph"
                       :description "Visualize bean dependencies."
                       :route "bean-graph"
                       :icon icon-graph
                       :icon-color "text-fuchsia-600 bg-fuchsia-50"
                       :component ui
                       :section "Beans (Container)"
                       :order 11})
                    ::beans-graph-ui]}}}))
