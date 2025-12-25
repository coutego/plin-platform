(ns plinpt.p-schema-visualizer.ui
  (:require [reagent.core :as r]
            [plinpt.p-schema-visualizer.core :as core]))

(defn diagram-view []
  (let [ref (r/atom nil)
        pan-zoom (r/atom nil)]
    (r/create-class
     {:component-did-mount
      (fn []
        (when (and @ref js/mermaid)
          (try
            (js/mermaid.initialize #js {:startOnLoad false :securityLevel "loose"})
            (js/mermaid.init js/undefined @ref)
            
            ;; Wait for Mermaid to render SVG
            (js/setTimeout
             (fn []
               (let [svg (.querySelector @ref "svg")]
                 (when svg
                   ;; Override Mermaid styles that prevent zooming
                   (set! (.-style svg) "max-width: none; height: 100%; width: 100%;")
                   
                   (when js/svgPanZoom
                     (let [instance (js/svgPanZoom svg 
                                                   #js {:zoomEnabled true
                                                        :controlIconsEnabled false
                                                        :fit true
                                                        :center true
                                                        :minZoom 0.1
                                                        :maxZoom 10})]
                       (reset! pan-zoom instance))))))
             100)
            (catch :default e
              (js/console.error "Mermaid error" e)))))
      
      :reagent-render
      (fn []
        (let [schema (core/get-schema)
              chart-def (core/generate-mermaid schema)]
          [:div {:class "flex flex-col h-[calc(100vh-64px)] bg-white"}
           [:div {:class "p-4 border-b bg-gray-50 flex justify-between items-center z-10 shadow-sm"}
            [:h2 {:class "text-lg font-bold text-gray-800"} "Database Schema"]
            [:div {:class "flex items-center gap-4"}
             [:div {:class "text-sm text-gray-500"} "Auto-generated from AlaSQL"]
             
             ;; Zoom Controls
             [:div {:class "flex rounded-md shadow-sm"}
              [:button {:class "px-3 py-1 bg-white border border-gray-300 rounded-l-md hover:bg-gray-50 text-sm font-medium text-gray-700"
                        :title "Zoom Out"
                        :on-click #(when @pan-zoom (.zoomOut @pan-zoom))} "-"]
              [:button {:class "px-3 py-1 bg-white border-t border-b border-gray-300 hover:bg-gray-50 text-sm font-medium text-gray-700"
                        :title "Reset Zoom"
                        :on-click #(when @pan-zoom (.resetZoom @pan-zoom) (.center @pan-zoom))} "Reset"]
              [:button {:class "px-3 py-1 bg-white border border-gray-300 rounded-r-md hover:bg-gray-50 text-sm font-medium text-gray-700"
                        :title "Zoom In"
                        :on-click #(when @pan-zoom (.zoomIn @pan-zoom))} "+"]]]]
           
           [:div {:class "flex-1 overflow-hidden bg-gray-100 relative"}
            [:div {:class "mermaid w-full h-full flex justify-center items-center"
                   :ref #(reset! ref %)}
             chart-def]]]))})))

(defn make-route [ui]
  {:path "/development/schema" :component ui :layout :full-screen})
