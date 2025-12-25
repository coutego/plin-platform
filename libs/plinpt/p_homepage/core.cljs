(ns plinpt.p-homepage.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- Error Boundary Implementation ---

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

;; --- Main Component ---

(defn default-guest-view []
  [:div {:class "text-center mt-10"}
   [:p {:class "text-xl text-gray-600"} "Please log in to use the application."]])

(defn home-page [features metrics planning-action reports-action can? user guest-content]
  (let [logged-in? (some? user)
        {:keys [component show-header?]} guest-content
        sorted-features (sort-by :order features)
        visible-features (if can?
                           (filter #(if (:required-perm %) (can? (:required-perm %)) true) sorted-features)
                           sorted-features)]
    [:div {:class "max-w-7xl mx-auto py-12 px-4 sm:px-6 lg:px-8"}
     (when (or logged-in? show-header?)
       [:div {:class "text-center"}
        [:h1 {:class "text-4xl tracking-tight font-extrabold text-gray-900 sm:text-5xl md:text-6xl"}
         [:span {:class "block xl:inline"} "Welcome to "]
         [:span {:class "block text-blue-600 xl:inline"} "PLIN Demo"]]
        [:p {:class "mt-3 max-w-md mx-auto text-base text-gray-500 sm:text-lg md:mt-5 md:text-xl md:max-w-3xl"}
         "A reference implementation of a modular Single Page Application (SPA) built using the Plin architecture."]])
      
     (if logged-in?
       [:div
        [:div {:class "mt-10 max-w-sm mx-auto sm:max-w-none sm:flex sm:justify-center gap-4"}
         (when planning-action
           [:a {:href (:href planning-action)
                :class "flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 md:py-4 md:text-lg md:px-10"}
            (:label planning-action)])
         (when reports-action
           [:a {:href (:href reports-action)
                :class "flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-md text-blue-700 bg-blue-100 hover:bg-blue-200 md:py-4 md:text-lg md:px-10"}
            (:label reports-action)])]

        ;; Metrics Section
        (when (seq metrics)
          [:div {:class "mt-12"}
           [:div {:class "grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-5"}
            (for [[i m] (map-indexed vector metrics)]
              ^{:key i} [error-boundary [m]])]])

        [:div {:class "mt-20"}
         [:div {:class "grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3"}
          (for [[idx feature] (map-indexed vector visible-features)]
            ^{:key idx}
            [:div {:class "pt-6"}
             (let [content [:div {:class "flow-root bg-gray-50 rounded-lg px-6 pb-8 h-full"}
                            [:div {:class "-mt-6"}
                             [:div {:class (str "inline-flex items-center justify-center p-3 rounded-md shadow-lg " 
                                                (or (:color-class feature) "bg-blue-500"))}
                              (if (:icon feature)
                                [error-boundary {:compact true} [(:icon feature)]]
                                ;; Default icon if none provided
                                [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                                 [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 10V3L4 14h7v7l9-11h-7z"}]])]
                             [:h3 {:class "mt-8 text-lg font-medium text-gray-900 tracking-tight"} (:title feature)]
                             [:p {:class "mt-5 text-base text-gray-500"}
                              (:description feature)]]]]
               (if (:href feature)
                 [:a {:href (if (str/starts-with? (:href feature) "/")
                              (str "#" (:href feature))
                              (:href feature))
                      :class "block h-full hover:opacity-90 transition-opacity"}
                  content]
                 content))])]]]
       
       ;; Guest Content
       (when component
         [:div {:class "mt-12"}
          [error-boundary [component]]]))]))

(defn create-home-page [features metrics planning reports can? user guest-content]
  (fn [] [home-page features metrics planning reports can? @user guest-content]))

(defn make-route [ui]
  {:path "/" :component ui})
