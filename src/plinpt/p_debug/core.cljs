(ns plinpt.p-debug.core
  (:require [reagent.core :as r]
            [plinpt.p-debug.common :as common]
            [plinpt.p-debug.plugins-ui :as plugins-ui]
            [plinpt.p-debug.plugin-graph :as plugin-graph]
            [plinpt.p-debug.beans-ui :as beans-ui]
            [plinpt.p-debug.bean-graph :as bean-graph]
            [plinpt.p-debug.raw-view :as raw-view]))

(def icon-debug common/icon-debug)

;; --- Individual Views ---
;; These are Form-1 components (simple functions that return hiccup)
;; They receive the api as an argument from partial application

(defn plugins-manage-view [api]
  [plugins-ui/main-view api])

(defn plugins-graph-view [api]
  [plugin-graph/main-view (:state api)])

(defn plugins-config-view [api]
  (let [all-plugins (:all-plugins @(:state api))]
    [raw-view/main-view all-plugins "Plugin Configuration" "Raw EDN definition of all loaded plugins."
     {:segmented? true
      :header-fn #(str "Plugin: " (:id %))
      :content-fn identity}]))

(defn beans-inspect-view [api]
  (let [state @(:state api)]
    [beans-ui/main-view (:container state) (:all-plugins state)]))

(defn beans-graph-view [api]
  [bean-graph/main-view (:state api)])

(defn beans-config-view [api]
  (let [container (:container @(:state api))
        definitions (get container :plin.core/definitions)]
    [raw-view/main-view definitions "Bean Definitions" "Bean definitions captured during container creation."
     {:segmented? true
      :header-fn #(str "Bean: " (first %))
      :content-fn second}]))

;; --- Landing Page ---

(defn debug-panel [api]
  [:div {:class "flex h-full items-center justify-center bg-gray-50 text-gray-500"}
   [:div {:class "text-center"}
    [:div {:class "mb-4 flex justify-center"}
     [:div {:class "p-4 bg-white rounded-full shadow-sm"}
      [icon-debug]]]
    [:h2 {:class "text-xl font-semibold text-gray-700"} "System Debugger"]
    [:p {:class "mt-2"} "Select a tool from the sidebar to inspect the system."]]])

(defn make-route [ui]
  {:path "/debug"
   :component ui
   :layout :full-screen})
