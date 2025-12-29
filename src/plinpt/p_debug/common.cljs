(ns plinpt.p-debug.common
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [clojure.edn :as edn]))

;; --- ID Formatting Utilities ---

(defn make-id-parts-formatter
  "Returns a function that takes an ID and returns a map {:prefix ... :body ... :suffix ...}.
   Currently simplified to just return the full ID as body, optionally stripping /plugin suffix."
  [_all-ids]
  (fn [id]
    (let [s (str id)
          body (if (str/ends-with? s "/plugin")
                 (subs s 0 (- (count s) 7)) ;; Remove "/plugin"
                 s)]
      {:prefix ""
       :body body
       :suffix ""})))

(defn make-id-formatter 
  "Returns a function that takes an ID and returns a display string (body only)."
  [all-ids]
  (let [formatter (make-id-parts-formatter all-ids)]
    (fn [id]
      (:body (formatter id)))))

;; --- Icons ---

(defn icon-debug []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]])

(defn icon-cube []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"}]])

(defn icon-puzzle []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M11 4a2 2 0 114 0v1a1 1 0 001 1h3a1 1 0 011 1v3a1 1 0 01-1 1h-1a2 2 0 100 4h1a1 1 0 011 1v3a1 1 0 01-1 1h-3a1 1 0 01-1-1v-1a2 2 0 10-4 0v1a1 1 0 01-1 1H7a1 1 0 01-1-1v-3a1 1 0 00-1-1H4a2 2 0 110-4h1a1 1 0 001-1V7a1 1 0 011-1h3a1 1 0 001-1V4z"}]])

(defn icon-graph []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z"}]])

(defn icon-code []
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]])

(defn icon-function []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"}]])

(defn icon-component []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:rect {:x "3" :y "3" :width "18" :height "18" :rx "2" :ry "2" :stroke-width "2"}]
   [:line {:x1 "3" :y1 "9" :x2 "21" :y2 "9" :stroke-width "2"}]
   [:line {:x1 "9" :y1 "21" :x2 "9" :y2 "9" :stroke-width "2"}]])

(defn icon-value []
  [:svg {:class "w-4 h-4" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M7 20l4-16m2 16l4-16M6 9h14M4 15h14"}]])

;; --- Layout Components ---

(defn sidebar-item [label icon active? on-click]
  [:button
   {:class (str "w-full flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors mb-1 "
                (if active?
                  "bg-purple-100 text-purple-700"
                  "text-gray-600 hover:bg-gray-100 hover:text-gray-900"))
    :on-click on-click}
   [:span {:class (str "mr-3 " (if active? "text-purple-500" "text-gray-400"))}
    [icon]]
   label])

(defn sidebar-group [title]
  [:div {:class "px-3 mb-2 mt-4"}
   [:h3 {:class "text-xs font-semibold text-gray-500 uppercase tracking-wider"}
    title]])

;; --- Mermaid Component ---

(defn mermaid-view [definition]
  (let [ref (r/atom nil)
        last-def (r/atom nil)
        pan-zoom (r/atom nil)
        
        render-graph (fn []
                       (when (and @ref definition)
                         (try
                           ;; Reset content to definition so mermaid can parse it
                           (set! (.-innerHTML @ref) definition)
                           ;; Remove processed attribute if present to force re-render
                           (.removeAttribute @ref "data-processed")
                           ;; Run mermaid
                           (.run js/mermaid #js {:nodes #js [@ref]})
                           
                           ;; Initialize PanZoom
                           (js/setTimeout
                            (fn []
                              (let [svg (.querySelector @ref "svg")]
                                (when svg
                                  ;; Clean up old instance
                                  (when @pan-zoom
                                    (.destroy @pan-zoom)
                                    (reset! pan-zoom nil))
                                  
                                  ;; Styles for pan-zoom
                                  (set! (.-style svg) "max-width: none; height: 100%; width: 100%;")
                                  
                                  (when (exists? js/svgPanZoom)
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
                             (js/console.error "Mermaid error" e)))))]
    (r/create-class
     {:component-did-mount
      (fn []
        (reset! last-def definition)
        (render-graph))
      
      :component-did-update
      (fn []
        (when (not= definition @last-def)
          (reset! last-def definition)
          (render-graph)))
      
      :component-will-unmount
      (fn []
        (when @pan-zoom
          (.destroy @pan-zoom)))

      :reagent-render
      (fn []
        [:div {:class "w-full h-full relative overflow-hidden bg-white"}
         ;; Zoom Controls
         [:div {:class "absolute top-4 right-4 z-10 flex rounded-md shadow-sm bg-white border border-gray-300"}
          [:button {:class "px-3 py-1 hover:bg-gray-50 text-gray-700 font-bold border-r border-gray-300"
                    :on-click #(when @pan-zoom (.zoomOut @pan-zoom))
                    :title "Zoom Out"} "-"]
          [:button {:class "px-3 py-1 hover:bg-gray-50 text-gray-700 font-medium border-r border-gray-300"
                    :on-click #(when @pan-zoom (.resetZoom @pan-zoom) (.center @pan-zoom))
                    :title "Reset"} "Reset"]
          [:button {:class "px-3 py-1 hover:bg-gray-50 text-gray-700 font-bold"
                    :on-click #(when @pan-zoom (.zoomIn @pan-zoom))
                    :title "Zoom In"} "+"]]
         
         ;; Graph Container
         [:div {:ref #(reset! ref %) 
                :class "mermaid w-full h-full flex justify-center items-center"}
          definition]])})))

;; --- Existing Components (Error Boundary, etc) ---

(defn ui-error-boundary [child on-reset]
  (let [error (r/atom nil)]
    (r/create-class
     {:display-name "UiErrorBoundary"
      :component-did-catch (fn [this e info]
                             (reset! error e))
      :reagent-render (fn [child on-reset]
                        (if @error
                          [:div {:class "p-2 bg-red-50 border border-red-200 rounded my-2"}
                           [:div {:class "text-red-600 text-xs font-bold"} "Render Error"]
                           [:div {:class "text-red-500 text-xs font-mono mb-2 overflow-auto break-all"} (str @error)]
                           [:button {:class "px-2 py-1 bg-white border border-red-300 text-red-700 text-xs rounded hover:bg-red-50"
                                     :on-click (fn []
                                                 (reset! error nil)
                                                 (when on-reset (on-reset)))}
                            "Reset"]]
                          child))})))

(defn render-result [res]
  [:div {:class "mt-2 p-2 bg-gray-100 rounded text-xs font-mono overflow-auto max-h-40"}
   (try
     (pr-str res)
     (catch :default e
       [:span.text-red-500 (str "Error printing value: " e)]))])

(defn promise-view [p]
  (let [state (r/atom {:status :pending :value nil})]
    (r/create-class
     {:component-did-mount
      (fn []
        (-> p
            (.then (fn [v] (reset! state {:status :resolved :value v})))
            (.catch (fn [e] (reset! state {:status :rejected :value e})))))
      
      :reagent-render
      (fn []
        (case (:status @state)
          :pending
          [:div {:class "flex items-center gap-1 text-gray-500 mt-2 p-2 bg-gray-100 rounded text-xs font-mono"}
           [:span "Resolving Promise"]
           [:span {:class "animate-bounce" :style {:animation-delay "0ms"}} "."]
           [:span {:class "animate-bounce" :style {:animation-delay "150ms"}} "."]
           [:span {:class "animate-bounce" :style {:animation-delay "300ms"}} "."]]
          
          :resolved
          [:div
           [:div {:class "text-xs text-green-600 font-bold mt-2"} "Promise Resolved:"]
           [render-result (:value @state)]]
          
          :rejected
          [:div
           [:div {:class "text-xs text-red-600 font-bold mt-2"} "Promise Rejected:"]
           [render-result (:value @state)]]))})))

(defn- get-default-args [api-meta]
  (if-let [args (:args api-meta)]
    (try
      (pr-str (mapv second args))
      (catch :default _ "[]"))
    "[]"))

(defn- render-signature [api-meta]
  (cond
    (nil? api-meta)
    [:div {:class "mb-2 text-xs text-orange-500 italic"} "API is not documented"]

    (empty? (:args api-meta))
    [:div {:class "mb-2 text-xs text-gray-500 font-mono"}
     [:span {:class "font-bold text-gray-600"} "Signature: "]
     [:span "[]"]
     (when-let [ret (:ret api-meta)]
       [:span {:class "ml-2"}
        [:span {:class "text-gray-400"} "-> "]
        [:span {:class "text-purple-600"} (str ret)]])]

    :else
    (let [args (:args api-meta)]
      [:div {:class "mb-2 text-xs text-gray-500 font-mono"}
       [:span {:class "font-bold text-gray-600"} "Signature: "]
       [:span "["]
       (doall
        (for [[i [name _ type]] (map-indexed vector args)]
          ^{:key i}
          [:span
           (when (pos? i) ", ")
           [:span {:class "text-blue-600"} name]
           (when type
             [:span {:class "text-gray-400"} (str " " type)])]))
       [:span "]"]
       (when-let [ret (:ret api-meta)]
         [:span {:class "ml-2"}
          [:span {:class "text-gray-400"} "-> "]
          [:span {:class "text-purple-600"} (str ret)]])])))

(defn function-caller [f api-meta]
  (let [args-state (r/atom (get-default-args api-meta))
        result-state (r/atom nil)
        error-state (r/atom nil)
        ;; Show input if API is undocumented OR if args are documented and not empty.
        show-input? (or (nil? api-meta)
                        (not (empty? (:args api-meta))))]
    (fn []
      [:div {:class "mt-2 border p-2 rounded bg-gray-50"}
       [:div {:class "text-xs font-bold mb-1"} "Call Function"]
       
       [render-signature api-meta]
       
       (when show-input?
         [:textarea {:class "w-full p-1 text-xs border rounded font-mono"
                     :rows 2
                     :value @args-state
                     :on-change #(reset! args-state (-> % .-target .-value))
                     :placeholder "Arguments vector e.g. [1 2]"}])
       
       [:div {:class "flex gap-2 mt-1"}
        [:button {:class "px-2 py-1 bg-blue-600 text-white text-xs rounded hover:bg-blue-700"
                  :on-click (fn []
                              (reset! result-state nil)
                              (reset! error-state nil)
                              (try
                                (let [args (if show-input?
                                             (edn/read-string @args-state)
                                             [])]
                                  (if (vector? args)
                                    (let [res (apply f args)]
                                      (if (instance? js/Promise res)
                                        (reset! result-state {:type :promise :value res :id (str (random-uuid))})
                                        (reset! result-state {:type :value :value res :id (str (random-uuid))})))
                                    (reset! error-state "Arguments must be a vector [...]")))
                                (catch :default e
                                  (reset! error-state (str e)))))}
         "Call"]]
       (when @error-state
         [:div {:class "mt-2 text-red-600 text-xs"} @error-state])
       (when @result-state
         (let [{:keys [type value id]} @result-state]
           [:div {:class "mt-2" :key id}
            [:div {:class "text-xs text-gray-500"} "Result:"]
            (if (= type :promise)
              [promise-view value]
              [render-result value])]))])))

(defn component-renderer [comp-fn api-meta]
  (let [args-state (r/atom (get-default-args api-meta))
        mounting? (r/atom false)
        error-state (r/atom nil)
        ;; Show input if API is undocumented OR if args are documented and not empty.
        show-input? (or (nil? api-meta)
                        (not (empty? (:args api-meta))))]
    (fn []
      [:div {:class "mt-2 border p-2 rounded bg-gray-50"}
       [:div {:class "text-xs font-bold mb-1"} "Render Component"]
       
       [render-signature api-meta]
       
       (when show-input?
         [:textarea {:class "w-full p-1 text-xs border rounded font-mono"
                     :rows 2
                     :value @args-state
                     :on-change #(reset! args-state (-> % .-target .-value))
                     :placeholder "Arguments vector e.g. [] or [{:prop 1}]"}])
       
       [:div {:class "flex gap-2 mt-1"}
        [:button {:class "px-2 py-1 bg-green-600 text-white text-xs rounded hover:bg-green-700"
                  :on-click (fn []
                              (reset! error-state nil)
                              (try
                                (let [args (if show-input?
                                             (edn/read-string @args-state)
                                             [])]
                                  (if (vector? args)
                                    (swap! mounting? not)
                                    (reset! error-state "Arguments must be a vector [...]")))
                                (catch :default e
                                  (reset! error-state (str e)))))}
         (if @mounting? "Unmount" "Render")]]
       (when @error-state
         [:div {:class "mt-2 text-red-600 text-xs"} @error-state])
       (when @mounting?
         [:div {:class "mt-2 border border-dashed border-gray-400 p-2 bg-white"}
          [ui-error-boundary
           (try
             (let [args (if show-input?
                          (edn/read-string @args-state)
                          [])]
               (into [comp-fn] args))
             (catch :default e
               [:div.text-red-500 (str "Render error: " e)]))
           #(reset! mounting? false)]])])))
