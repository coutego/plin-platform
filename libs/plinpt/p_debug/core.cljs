(ns plinpt.p-debug.core
  (:require [reagent.core :as r]
            [plinpt.p-debug.common :as common]
            [plinpt.p-debug.plugins-ui :as plugins-ui]
            [plinpt.p-debug.plugin-graph :as plugin-graph]
            [plinpt.p-debug.beans-ui :as beans-ui]
            [plinpt.p-debug.bean-graph :as bean-graph]
            [plinpt.p-debug.raw-view :as raw-view]))

(def icon-debug common/icon-debug)

(defn debug-panel [api]
  (let [{:keys [state reload!]} api
        active-view (r/atom :plugins-manage)]
    (fn []
      (let [{:keys [container all-plugins]} @state]
        [:div {:class "flex h-screen bg-gray-100 overflow-hidden"}
         
         ;; --- Sidebar ---
         [:div {:class "w-64 bg-white border-r border-gray-200 flex flex-col flex-shrink-0"}
          [:div {:class "h-14 flex items-center px-4 border-b border-gray-200"}
           [:div {:class "text-purple-600 mr-2"} [icon-debug]]
           [:h1 {:class "font-bold text-lg text-gray-800"} "System Debugger"]]
          
          [:div {:class "flex-1 overflow-y-auto py-4"}
           
           [common/sidebar-group "Plugins"]
           [common/sidebar-item "Manage" common/icon-puzzle (= @active-view :plugins-manage) #(reset! active-view :plugins-manage)]
           [common/sidebar-item "Dependency Graph" common/icon-graph (= @active-view :plugins-graph) #(reset! active-view :plugins-graph)]
           [common/sidebar-item "Configuration" common/icon-code (= @active-view :plugins-config) #(reset! active-view :plugins-config)]
           
           [common/sidebar-group "Container (Beans)"]
           [common/sidebar-item "Inspector" common/icon-cube (= @active-view :beans-inspect) #(reset! active-view :beans-inspect)]
           [common/sidebar-item "Dependency Graph" common/icon-graph (= @active-view :beans-graph) #(reset! active-view :beans-graph)]
           [common/sidebar-item "Configuration" common/icon-code (= @active-view :beans-config) #(reset! active-view :beans-config)]]]
         
         ;; --- Main Content ---
         [:div {:class "flex-1 overflow-hidden"}
          (case @active-view
            :plugins-manage [plugins-ui/main-view state reload!]
            :plugins-graph  [plugin-graph/main-view state]
            :plugins-config [raw-view/main-view all-plugins "Plugin Configuration" "Raw EDN definition of all loaded plugins."
                             {:segmented? true
                              :header-fn #(str "Plugin: " (:id %))
                              :content-fn identity}]
            
            :beans-inspect  [beans-ui/main-view container all-plugins]
            :beans-graph    [bean-graph/main-view state]
            :beans-config   [raw-view/main-view (get container :plin.core/definitions) "Bean Definitions" "Bean definitions captured during container creation."
                             {:segmented? true
                              :header-fn #(str "Bean: " (first %))
                              :content-fn second}]
            
            [:div "Unknown view"])]]))))

(defn make-route [ui]
  {:path "/debug"
   :component ui
   :layout :full-screen})
