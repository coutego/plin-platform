(ns plinpt.p-app-shell.core
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.p-app-shell.doc :as doc]))

;; --- Router State ---
(defonce current-route (r/atom nil))

(defn- normalize-hash [hash]
  (if (or (str/blank? hash) (= hash "#"))
    "/"
    (str/replace hash #"^#" "")))

(defn- match-route [route-defs path]
  (or (some (fn [def]
              (let [def-path (:path def)]
                (if (and def-path (str/includes? def-path ":"))
                  ;; Handle routes with parameters (e.g. /history/:id)
                  (let [path-parts (str/split path #"/")
                        def-parts (str/split def-path #"/")]
                    (when (= (count path-parts) (count def-parts))
                      (when (every? (fn [[p d]] 
                                      (or (= p d) 
                                          (str/starts-with? d ":")))
                                    (map vector path-parts def-parts))
                        def)))
                  ;; Handle exact matches
                  (when (= def-path path)
                    def))))
            route-defs)
      nil))

(defn- handle-hash-change [route-defs]
  (let [path (normalize-hash (.-hash js/location))
        match (match-route route-defs path)]
    (reset! current-route match)))

;; --- Components ---

(defn not-found-view []
  [:div {:class "flex flex-col items-center justify-center h-64 text-gray-500"}
   [:h2 {:class "text-2xl font-bold mb-2"} "404"]
   [:p "Page not found"]])

(defn unauthorized-view []
  [:div {:class "flex flex-col items-center justify-center h-64 text-gray-500"}
   [:h2 {:class "text-2xl font-bold mb-2"} "403"]
   [:p "You do not have permission to view this page."]])

(defn redirect-home []
  (r/create-class
   {:component-did-mount
    (fn []
      (set! (.-hash js/location) "/"))
    :reagent-render
    (fn []
      [:div])}))

;; --- Error Boundary ---

(defn error-modal [error close-fn]
  [:div {:class "fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50 backdrop-blur-sm"
         :on-click close-fn}
   [:div {:class "bg-white rounded-lg shadow-xl max-w-2xl w-full m-4 flex flex-col max-h-screen"
          :on-click #(.stopPropagation %)}
    [:div {:class "p-4 border-b flex justify-between items-center bg-gray-50 rounded-t-lg"}
     [:h3 {:class "font-bold text-gray-700"} "Error Stack Trace"]
     [:button {:class "text-gray-400 hover:text-gray-600" :on-click close-fn} "✕"]]
    [:div {:class "p-4 overflow-auto bg-gray-900 text-green-400 font-mono text-xs" :style {:max-height "60vh"}}
     (str (.-stack error))]
    [:div {:class "p-4 border-t bg-gray-50 text-right rounded-b-lg"}
     [:button {:class "px-4 py-2 bg-gray-200 hover:bg-gray-300 rounded text-gray-700 font-medium"
               :on-click close-fn} "Close"]]]])

(defn block-error-ui [state error]
  [:div {:class "p-4 bg-red-50 border border-red-200 rounded-lg shadow-sm flex items-start gap-3"}
   [:button {:class "text-red-500 hover:text-red-700 mt-1"
             :on-click #(swap! state update :show-details not) :title "View Details"}
    [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
     [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
             :d "M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"}]]]
   [:div {:class "flex-1"}
    [:h3 {:class "text-red-800 font-bold"} "Application Error"]
    [:p {:class "text-red-600 text-sm mt-1"} "Something went wrong in this section."]
    [:button {:class "mt-3 px-3 py-1 bg-red-600 text-white text-xs font-bold rounded hover:bg-red-700"
              :on-click #(reset! state {:has-error false :error nil :show-details false})}
     "TRY AGAIN"]]])

(defn compact-error-ui [state error]
  [:span {:class "inline-flex items-center gap-2"}
   [:button {:class "text-red-500 hover:text-red-700 bg-red-100 rounded px-2 py-0.5 text-xs font-bold border border-red-200"
             :on-click #(swap! state update :show-details not)
             :title "Error! Click for details."}
    "⚠ Error"]
   [:button {:class "text-gray-400 hover:text-gray-600 text-xs underline"
             :on-click #(reset! state {:has-error false :error nil :show-details false})
             :title "Try running this component again"}
    "Retry"]])

(defn error-boundary [& args]
  (let [[props children] (if (map? (first args))
                           [(first args) (rest args)]
                           [{} args])
        state (r/atom {:has-error false :error nil :show-details false})]

    (r/create-class
      {:display-name "ErrorBoundary"

       :component-did-catch
       (fn [this error info]
         (js/console.error "Error Boundary caught:" error)
         (swap! state assoc :has-error true :error error))

       :reagent-render
       (fn [& args]
         (let [{:keys [has-error error show-details]} @state
               ;; Re-parse args in render to ensure we use latest props
               [current-props children] (if (map? (first args))
                                    [(first args) (rest args)]
                                    [{} args])
               is-compact (:compact current-props)]

           (if has-error
             [:<>
              ;; Choose UI based on :compact prop
              (if is-compact
                [compact-error-ui state error]
                [block-error-ui state error])

              ;; Render Modal if requested (same for both)
              (when show-details
                [error-modal error #(swap! state assoc :show-details false)])]

             ;; Success State
             (into [:<>] children))))})))

(defn app-layout [header routes overlays can? user-atom breadcrumb]
  ;; Form-2 Component: Setup phase
  (let [hash-handler #(handle-hash-change routes)]
    (set! (.-onhashchange js/window) hash-handler)
    (handle-hash-change routes)
    
    ;; Return the render function
    (fn [& _]
      (let [active-route @current-route
            Page (:component active-route)
            layout-mode (:layout active-route)
            required-perm (:required-perm active-route)
            user @user-atom]
        [:div {:class "min-h-screen bg-gray-50 font-sans flex flex-col"}
         ;; Global Header Region
         [:header {:class "bg-blue-900 text-white shadow-md sticky top-0 z-50 flex-shrink-0"}
          [:div {:class "max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16"}
           [:div {:class "flex items-center justify-between h-full"}
            ;; We render all header components injected by plugins
            (doall
             (for [[idx comp] (map-indexed vector header)]
               ^{:key idx} [error-boundary [comp]]))]]
          
          ;; Breadcrumb Bar (if present)
          (when breadcrumb
            [error-boundary [breadcrumb]])]

         ;; Main Content Region
         (let [content (cond
                         (not Page) [not-found-view]
                         ;; If route requires permission but no user is logged in -> Redirect Home
                         (and required-perm (nil? user)) [redirect-home]
                         ;; If route requires permission and user lacks it -> 403
                         (and required-perm (not (can? required-perm))) [unauthorized-view]
                         :else [Page])]
           (if (= layout-mode :full-screen)
             ;; Full Screen Layout (for Sidebar pages)
             ;; Calculates height to fill screen minus header (100vh - 4rem)
             [:main {:class "flex-1 h-[calc(100vh-4rem)] w-full overflow-hidden"}
              [error-boundary content]]
             
             ;; Default Document Layout (Centered, Padded)
             [:main {:class "flex-1 max-w-7xl mx-auto py-6 sm:px-6 lg:px-8 w-full"}
              [error-boundary content]]))
         
         ;; Global Overlays Region (e.g. Notifications, Modals)
         (doall
          (for [[idx comp] (map-indexed vector overlays)]
            ^{:key idx} [error-boundary [comp]]))]))))

(defn mount-app [root-component-fn]
  (fn []
    (if root-component-fn
      (try
        (rdom/render [root-component-fn] (.getElementById js/document "app"))
        (catch :default e
          (println "ERROR mounting app:" e)))
      (println "ERROR: root-component-fn is nil!"))))
