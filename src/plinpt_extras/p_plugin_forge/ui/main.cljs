(ns plinpt-extras.p-plugin-forge.ui.main
  "Main UI component with tabbed interface."
  (:require [reagent.core :as r]
            [plinpt-extras.p-plugin-forge.core :as core]
            [plinpt-extras.p-plugin-forge.ui.chat-page :as chat-page]
            [plinpt-extras.p-plugin-forge.ui.library-page :as library-page]
            [plinpt-extras.p-plugin-forge.ui.config-page :as config-page]))

(defn tab-button [tab-key label icon current-tab]
  [:button
   {:class (str "flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg transition-colors "
                (if (= tab-key current-tab)
                  "bg-blue-100 text-blue-700"
                  "text-slate-600 hover:bg-slate-100"))
    :on-click #(core/set-active-tab! tab-key)}
   icon
   label])

(defn cost-display []
  (let [chat-cost (core/get-chat-cost)
        session-cost (core/get-session-cost)]
    [:div {:class "flex items-center gap-4 text-xs text-slate-500"}
     [:span (str "Chat: $" (.toFixed chat-cost 4))]
     [:span (str "Session: $" (.toFixed session-cost 4))]]))

(defn header []
  [:div {:class "flex items-center justify-between border-b border-slate-200 pb-4 mb-4"}
   [:div {:class "flex items-center gap-3"}
    [:div {:class "p-2 bg-gradient-to-br from-purple-500 to-blue-500 rounded-lg"}
     [:svg {:class "w-6 h-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
      [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
              :d "M13 10V3L4 14h7v7l9-11h-7z"}]]]
    [:div
     [:h1 {:class "text-xl font-bold text-slate-900"} "Plugin Forge"]
     [:p {:class "text-sm text-slate-500"} "AI-powered plugin creator"]]]
   [cost-display]])

(defn tabs []
  (let [current-tab (core/get-active-tab)]
    [:div {:class "flex gap-2 mb-4"}
     [tab-button :chat "Chat"
      [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
               :d "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"}]]
      current-tab]
     [tab-button :library "Library"
      [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
               :d "M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"}]]
      current-tab]
     [tab-button :settings "Settings"
      [:svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
               :d "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"}]
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
               :d "M15 12a3 3 0 11-6 0 3 3 0 016 0z"}]]
      current-tab]]))

(defn tab-content []
  (let [current-tab (core/get-active-tab)]
    (case current-tab
      :chat [chat-page/chat-page]
      :library [library-page/library-page]
      :settings [config-page/config-page]
      [chat-page/chat-page])))

(defn forge-page []
  [:div {:class "p-6 max-w-5xl mx-auto h-full flex flex-col"}
   [header]
   [tabs]
   [:div {:class "flex-1 min-h-0"}
    [tab-content]]])
