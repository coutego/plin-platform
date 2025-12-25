(ns plinpt.p-nav-bar.core
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-authorization :as iauth]
            [plinpt.i-nav-bar :as inav]
            [plinpt.i-app-shell :as iapp]))


;; --- Error Boundary Implementation (Duplicated for independence) ---

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

;; --- Components ---

(defn nav-link [item]
  [:a {:href (str "#" (:route item))
       :class "text-blue-100 hover:bg-blue-800 hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors"}
   (:label item)])

(defn nav-bar [icon title items user-widget can? user]
  (let [sorted-items (sort-by :order items)
        visible-items (if can?
                        (filter #(if (:required-perm %) (can? (:required-perm %)) true) sorted-items)
                        sorted-items)]
    [:<>
     ;; Logo / Brand
     [:a {:href "#/" :class "flex items-center gap-3 hover:opacity-90 transition-opacity"}
      [:div {:class "w-8 h-8 bg-yellow-400 rounded-full flex items-center justify-center text-blue-900 font-bold text-xs"} (or icon "PL")]
      [:span {:class "font-bold text-xl tracking-tight"} (str title) ]]
     
     ;; Navigation Links
     (when user
       [:div {:class "hidden md:block"}
        [:div {:class "ml-10 flex items-baseline space-x-4"}
         (for [[idx item] (map-indexed vector visible-items)]
           ^{:key idx} [nav-link item])]])
     
     ;; User Widget
     [:div {:class "flex items-center gap-4"}
      (if user-widget
        [error-boundary {:compact true} [user-widget]]
        [:div {:class "text-blue-300 text-sm"} "No user"])]]))

(defn create-nav-bar [icon title items widget can? user]
  (fn [& _] [nav-bar icon title items widget can? @user]))
