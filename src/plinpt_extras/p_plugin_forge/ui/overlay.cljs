(ns plinpt-extras.p-plugin-forge.ui.overlay
  "Draggable overlay panel for hot-loaded plugin control."
  (:require [reagent.core :as r]
            [plinpt-extras.p-plugin-forge.core :as core]))

(defonce drag-state (r/atom {:start-x 0 :start-y 0 :start-pos-x 0 :start-pos-y 0}))
(defonce toggling? (r/atom false))

(defn start-drag! [e]
  (let [overlay (core/get-overlay)
        pos (:position overlay)]
    (.preventDefault e)
    (reset! drag-state {:start-x (.-clientX e)
                        :start-y (.-clientY e)
                        :start-pos-x (:x pos)
                        :start-pos-y (:y pos)})
    (core/set-overlay-dragging! true)))

(defn on-drag! [e]
  (when (:dragging? (core/get-overlay))
    (let [{:keys [start-x start-y start-pos-x start-pos-y]} @drag-state
          dx (- (.-clientX e) start-x)
          dy (- (.-clientY e) start-y)
          new-x (max 0 (+ start-pos-x dx))
          new-y (max 0 (+ start-pos-y dy))]
      (core/set-overlay-position! new-x new-y))))

(defn stop-drag! []
  (core/set-overlay-dragging! false))

(defn get-actual-plugin-id
  "Get the actual plugin ID from the plugin definition.
   The boot system uses :id from the plugin def (e.g., :my.namespace/plugin),
   not just the namespace keyword."
  [hot-loaded]
  (let [plugin-def (:plugin-def hot-loaded)]
    (when plugin-def
      (:id plugin-def))))

(defn toggle-and-reload!
  "Toggle the enabled state and force a plugin reload using the proper boot API."
  []
  (when-not @toggling?
    (reset! toggling? true)
    (let [sys-api (core/get-sys-api)
          {:keys [disable-plugin! enable-plugin! reload!]} sys-api
          hot-loaded (core/get-hot-loaded)
          ;; Use the actual plugin ID from the plugin definition
          actual-plugin-id (get-actual-plugin-id hot-loaded)
          currently-enabled? (:enabled? hot-loaded)]
      ;; Update our local UI state
      (core/toggle-hot-loaded-enabled!)
      ;; Use the proper boot API to enable/disable the plugin
      (when (and sys-api actual-plugin-id)
        (if currently-enabled?
          (disable-plugin! actual-plugin-id)
          (enable-plugin! actual-plugin-id)))
      ;; Give state time to update, then reload
      (js/setTimeout
       (fn []
         (when reload!
           (reload!))
         (reset! toggling? false))
       100))))

(defn toggle-switch [enabled? on-toggle disabled?]
  [:button
   {:class (str "relative inline-flex h-6 w-11 items-center rounded-full transition-colors "
                (if disabled? "opacity-50 cursor-not-allowed " "")
                (if enabled? "bg-green-500" "bg-slate-300"))
    :disabled disabled?
    :on-click (when-not disabled? on-toggle)}
   [:span {:class (str "inline-block h-4 w-4 transform rounded-full bg-white transition-transform "
                       (if enabled? "translate-x-6" "translate-x-1"))}]])

(defn error-display []
  (let [hot-loaded (core/get-hot-loaded)
        error (:error hot-loaded)
        expanded? (:error-expanded? hot-loaded)]
    (when error
      [:div {:class "mt-3 p-3 bg-red-50 border border-red-200 rounded-lg"}
       [:div {:class "flex items-start justify-between"}
        [:div {:class "flex items-center gap-2"}
         [:svg {:class "w-4 h-4 text-red-500" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                  :d "M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]
         [:span {:class "text-sm font-medium text-red-700"} "Load Error"]]
        [:button {:class "text-xs text-red-600 hover:text-red-700"
                  :on-click core/toggle-error-expanded!}
         (if expanded? "Hide" "Details")]]
       (when expanded?
         [:pre {:class "mt-2 text-xs text-red-600 overflow-x-auto whitespace-pre-wrap"}
          error])])))

(defn overlay-panel []
  (let [hot-loaded (core/get-hot-loaded)
        overlay (core/get-overlay)
        {:keys [plugin-id enabled? code]} hot-loaded
        {:keys [visible? position]} overlay
        is-toggling? @toggling?
        ;; Get the actual plugin ID for display
        actual-id (get-actual-plugin-id hot-loaded)
        display-id (or actual-id plugin-id)]
    (when (and visible? plugin-id)
      [:div
       {:class "fixed z-50 bg-white rounded-xl shadow-2xl border border-slate-200 w-80"
        :style {:left (str (:x position) "px")
                :top (str (:y position) "px")}
        :on-mouse-move on-drag!
        :on-mouse-up stop-drag!
        :on-mouse-leave stop-drag!}

       ;; Header (draggable)
       [:div {:class "flex items-center justify-between px-4 py-3 bg-slate-50 rounded-t-xl cursor-move border-b border-slate-200"
              :on-mouse-down start-drag!}
        [:div {:class "flex items-center gap-2"}
         [:svg {:class "w-4 h-4 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                  :d "M4 8h16M4 16h16"}]]
         [:span {:class "font-medium text-slate-700 text-sm"}
          "Plugin Test"]]
        [:button {:class "p-1 hover:bg-slate-200 rounded transition-colors"
                  :on-click #(core/set-overlay-visible! false)}
         [:svg {:class "w-4 h-4 text-slate-400" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
          [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                  :d "M6 18L18 6M6 6l12 12"}]]]]

       ;; Content
       [:div {:class "p-4"}
        ;; Plugin ID (full)
        [:div {:class "mb-3 p-2 bg-slate-100 rounded-lg"}
         [:p {:class "text-xs font-medium text-slate-500 uppercase mb-1"} "Plugin ID"]
         [:p {:class "text-sm font-mono text-slate-700 break-all"} (str display-id)]]
        
        ;; Status indicator
        [:div {:class (str "flex items-center gap-2 mb-4 p-2 rounded-lg "
                           (cond
                             is-toggling? "bg-blue-50"
                             enabled? "bg-green-50"
                             :else "bg-amber-50"))}
         [:div {:class (str "w-2 h-2 rounded-full "
                            (cond
                              is-toggling? "bg-blue-500 animate-spin"
                              enabled? "bg-green-500 animate-pulse"
                              :else "bg-amber-500"))}]
         [:span {:class (str "text-sm "
                             (cond
                               is-toggling? "text-blue-700"
                               enabled? "text-green-700"
                               :else "text-amber-700"))}
          (cond
            is-toggling? "Reloading Plugins..."
            enabled? "Plugin Active"
            :else "Plugin Disabled")]]

        ;; Enable/Disable toggle
        [:div {:class "flex items-center justify-between mb-4"}
         [:div
          [:span {:class "text-sm text-slate-600"}
           (if enabled? "Enabled" "Disabled")]
          [:p {:class "text-xs text-slate-400"}
           (if enabled?
             "Plugin is running normally"
             "Plugin routes are hidden")]]
         [toggle-switch enabled? toggle-and-reload! is-toggling?]]

        [error-display]

        ;; Info about behavior
        (when-not enabled?
          [:div {:class "mt-4 p-3 bg-slate-50 border border-slate-200 rounded-lg"}
           [:div {:class "flex items-start gap-2"}
            [:svg {:class "w-4 h-4 text-slate-500 mt-0.5 flex-shrink-0" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
             [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                     :d "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]
            [:p {:class "text-xs text-slate-600"}
             "When disabled, the plugin's navigation items are hidden. Re-enable to restore access."]]])

        ;; Info about reload
        [:div {:class "mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg"}
         [:div {:class "flex items-start gap-2"}
          [:svg {:class "w-4 h-4 text-blue-500 mt-0.5 flex-shrink-0" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
           [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
                   :d "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]]
          [:p {:class "text-xs text-blue-700"}
           "Toggling will reload all plugins to apply the change. The page may briefly update."]]]

        ;; Actions
        [:div {:class "flex gap-2 mt-4"}
         [:button
          {:class "flex-1 px-3 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg transition-colors"
           :on-click #(do
                        (when code
                          (core/save-plugin-to-library! plugin-id code))
                        (js/alert "Saved to library!"))}
          "Save to Library"]
         [:button
          {:class "flex-1 px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
           :on-click #(core/set-overlay-visible! false)}
          "Close"]]]])))

(defn draggable-panel []
  [overlay-panel])
