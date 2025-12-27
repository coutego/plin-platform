(ns showcase.command-palette
  (:require [reagent.core :as r]
            [clojure.string :as str]
            [plin.core :as plin]
            [plinpt.i-app-shell :as iapp]))

;; --- State ---

(defonce state (r/atom {:open? false
                        :query ""
                        :selected-index 0}))

;; --- Logic ---

(defn toggle! []
  (swap! state update :open? not)
  (when (:open? @state)
    (swap! state assoc :query "" :selected-index 0)))

(defn close! []
  (swap! state assoc :open? false))

(defn navigate! [route]
  (when route
    (set! (.-hash js/location) (:path route))
    (close!)))

(defn fuzzy-match? [query text]
  (let [q (str/replace (str/lower-case query) #"\s+" "")
        t (str/lower-case text)]
    (loop [qi 0 ti 0]
      (cond
        (= qi (count q)) true
        (= ti (count t)) false
        (= (nth q qi) (nth t ti)) (recur (inc qi) (inc ti))
        :else (recur qi (inc ti))))))

(defn filter-routes [routes query]
  (let [candidates (->> routes
                        (filter :path)
                        (sort-by :path))]
    (if (str/blank? query)
      (take 100 candidates)
      (->> candidates
           (filter #(fuzzy-match? query (:path %)))
           (take 100)))))

(defn handle-input-keydown [e filtered selected-index]
  (let [max-idx (dec (count filtered))]
    (condp = (.-key e)
      "ArrowDown" (do (.preventDefault e)
                      (swap! state update :selected-index #(min (inc %) max-idx)))
      "ArrowUp"   (do (.preventDefault e)
                      (swap! state update :selected-index #(max (dec %) 0)))
      "Enter"     (do (.preventDefault e)
                      (navigate! (nth filtered selected-index nil)))
      "Escape"    (close!)
      nil)))

;; --- Sub-Components ---

(defn search-input [query filtered selected-index]
  [:div {:class "flex items-center border-b border-slate-700 px-4"}
   [:svg {:class "w-5 h-5 text-slate-500 mr-3" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
    [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"}]]
   [:input {:id "cmd-palette-input"
            :type "text"
            :auto-focus true
            :class "flex-1 h-14 outline-none text-white placeholder:text-slate-500 bg-transparent text-lg"
            :placeholder "Go to..."
            :value query
            :on-change #(swap! state assoc :query (.. % -target -value) :selected-index 0)
            :on-key-down #(handle-input-keydown % filtered selected-index)}]])

(defn result-item [idx route selected-index]
  (let [selected? (= idx selected-index)]
    [:div {:id (when selected? "cmd-palette-selected")
           :class (str "px-4 py-3 mx-2 rounded-lg cursor-pointer flex items-center justify-between transition-colors "
                       (if selected?
                         "bg-blue-600 text-white"
                         "text-slate-300 hover:bg-slate-700/50"))
           :on-click #(navigate! route)
           :on-mouse-move #(when-not selected?
                             (swap! state assoc :selected-index idx))}
     
     [:div {:class "flex items-center gap-3"}
      [:svg {:class (str "w-4 h-4 " (if selected? "text-blue-200" "text-slate-500")) 
             :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
       [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M13 7l5 5m0 0l-5 5m5-5H6"}]]
      
      [:span {:class "font-mono text-sm"} (:path route)]]
     
     (when selected?
       [:span {:class "text-xs text-blue-200 font-bold opacity-75"} "⏎"])]))

(defn results-list [filtered selected-index]
  (if (empty? filtered)
    [:div {:class "p-8 text-center text-slate-500"} 
     [:div "No matching routes found."]]
    
    ;; Added [&::-webkit-scrollbar]:hidden to hide scrollbar in WebKit
    ;; Added style for Firefox/IE
    [:div {:class "max-h-[60vh] overflow-y-auto py-2 scroll-smooth [&::-webkit-scrollbar]:hidden"
           :style {:scrollbar-width "none" :ms-overflow-style "none"}}
     (doall
      (for [[idx route] (map-indexed vector filtered)]
        ^{:key (:path route)}
        [result-item idx route selected-index]))]))

(defn palette-footer []
  [:div {:class "bg-slate-900/50 px-4 py-2 text-xs text-slate-500 flex justify-between border-t border-slate-700"}
   [:div {:class "flex gap-3"}
    [:span "↑↓ to navigate"]
    [:span "↵ to select"]
    [:span "esc to close"]]
   [:span "PLIN Command Palette"]])

;; --- Main Component ---

(defn palette-component [routes]
  (let [listener-fn (atom nil)]
    (r/create-class
     {:component-did-mount
      (fn []
        (let [handler (fn [e]
                        (when (and (or (.-ctrlKey e) (.-metaKey e))
                                   (= (.-key e) "k"))
                          (.preventDefault e)
                          (toggle!)))]
          (reset! listener-fn handler)
          (js/window.addEventListener "keydown" handler)))

      :component-will-unmount
      (fn []
        (when @listener-fn
          (js/window.removeEventListener "keydown" @listener-fn)))

      :component-did-update
      (fn []
        (when (:open? @state)
          (when-let [el (.getElementById js/document "cmd-palette-selected")]
            (.scrollIntoView el #js {:block "nearest"}))))
      
      :reagent-render
      (fn []
        (let [{:keys [open? query selected-index]} @state
              filtered (filter-routes routes query)]

          (when open?
            [:div {:class "fixed inset-0 z-[100] flex items-start justify-center pt-[15vh] font-sans"}
             ;; Backdrop
             [:div {:class "absolute inset-0 bg-black/60 backdrop-blur-sm transition-opacity"
                    :on-click close!}]

             ;; Modal
             [:div {:class "relative w-full max-w-xl bg-slate-800 border border-slate-700 rounded-xl shadow-2xl overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-100"}
              [search-input query filtered selected-index]
              [results-list filtered selected-index]
              [palette-footer]]])))})))

(def plugin
  (plin/plugin
   {:doc "A global command palette triggered by Ctrl+K / Cmd+K."
    :deps [iapp/plugin]
    
    :contributions
    {::iapp/overlay-components [::ui]}
    
    :beans
    {::ui
     ^{:doc "The Command Palette Overlay Component."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [partial palette-component ::iapp/routes]}}))
