(ns showcase.snake
  (:require [plin.core :as plin]
            [reagent.core :as r]
            [plinpt.i-application :as iapp]
            [plinpt.i-devtools :as idev]
            [plinpt.i-app-shell :as shell]))

;; --- Game Constants & Logic ---

(def board-width 20)
(def board-height 20)
(def initial-snake [[10 10] [10 11] [10 12]])
(def initial-dir [0 -1]) ;; Moving Up
(def tick-rate 150)

(defn rand-pos []
  [(rand-int board-width) (rand-int board-height)])

(defn new-game []
  {:snake initial-snake
   :direction initial-dir
   :food (rand-pos)
   :score 0
   :game-over? false
   :paused? false})

(defonce state (r/atom (new-game)))

(defn key->dir [k current-dir]
  (let [[dx dy] current-dir]
    (case k
      "ArrowUp"    (if (zero? dy) [0 -1] current-dir)
      "ArrowDown"  (if (zero? dy) [0 1] current-dir)
      "ArrowLeft"  (if (zero? dx) [-1 0] current-dir)
      "ArrowRight" (if (zero? dx) [1 0] current-dir)
      nil)))

(defn move-snake [{:keys [snake direction food score] :as g}]
  (let [[head-x head-y] (first snake)
        [dx dy] direction
        new-head [(+ head-x dx) (+ head-y dy)]]
    (cond
      ;; Collision with walls
      (or (< (first new-head) 0)
          (>= (first new-head) board-width)
          (< (second new-head) 0)
          (>= (second new-head) board-height)
          ;; Collision with self (excluding tail which moves away)
          (some #{new-head} (drop-last snake)))
      (assoc g :game-over? true)

      ;; Eat food
      (= new-head food)
      (-> g
          (update :snake #(cons new-head %)) ;; Grow
          (update :score inc)
          (assoc :food (rand-pos)))

      ;; Move normal
      :else
      (assoc g :snake (cons new-head (drop-last snake))))))

(defn tick! []
  (when-not (or (:game-over? @state) (:paused? @state))
    (swap! state move-snake)))

;; --- UI Components ---

(defn cell [x y snake food]
  (let [is-snake? (some #{[x y]} snake)
        is-head? (= [x y] (first snake))
        is-food? (= [x y] food)]
    [:div
     {:style {:width "100%"
              :height "100%"
              :background-color (cond
                                  is-head? "#4ade80" ;; Green-400
                                  is-snake? "#86efac" ;; Green-300
                                  is-food? "#f87171" ;; Red-400
                                  :else "#1e293b") ;; Slate-800
              :border-radius (if is-food? "50%" (if is-snake? "4px" "0"))
              :transition "all 0.1s"}}]))

(defn board-view []
  (let [{:keys [snake food]} @state]
    [:div
     {:style {:display "grid"
              :grid-template-columns (str "repeat(" board-width ", 1fr)")
              :grid-template-rows (str "repeat(" board-height ", 1fr)")
              :gap "1px"
              :width "100%"
              :max-width "500px"
              :aspect-ratio "1/1"
              :background-color "#334155" ;; Slate-700
              :border "4px solid #475569"
              :border-radius "8px"
              :padding "4px"}}
     (doall
      (for [y (range board-height)
            x (range board-width)]
        ^{:key (str x "-" y)}
        [cell x y snake food]))]))

(defn controls []
  [:div {:class "flex gap-4 mt-6"}
   [:button
    {:class "px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded transition"
     :on-click #(swap! state update :paused? not)}
    (if (:paused? @state) "Resume" "Pause")]
   [:button
    {:class "px-4 py-2 bg-slate-600 hover:bg-slate-700 text-white rounded transition"
     :on-click #(reset! state (new-game))}
    "Restart"]])

(defn game-over-overlay []
  (when (:game-over? @state)
    [:div {:class "absoluteinset-0 z-10 flex flex-col items-center justify-center bg-black/70 backdrop-blur-sm rounded-lg"
           :style {:position "absolute" :top 0 :left 0 :width "100%" :height "100%"}}
     [:h2 {:class "text-4xl text-red-500 font-bold mb-4"} "Game Over"]
     [:div {:class "text-white text-xl mb-6"} (str "Score: " (:score @state))]
     [:button
      {:class "px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-bold rounded-lg shadow-lg transition transform hover:scale-105"
       :on-click #(reset! state (new-game))}
      "Play Again"]]))

(defn snake-game-component []
  (r/create-class
   {:component-did-mount
    (fn []
      ;; Reset game on mount
      (reset! state (new-game))
      ;; Setup Key Listener
      (js/window.addEventListener "keydown"
        (fn [e]
          (let [k (.-key e)]
            (when (#{"ArrowUp" "ArrowDown" "ArrowLeft" "ArrowRight"} k)
              (.preventDefault e)
              (swap! state update :direction #(or (key->dir k %) %))))))
      ;; Setup Timer
      (defonce timer (js/setInterval tick! tick-rate)))

    :component-will-unmount
    (fn []
      (js/clearInterval timer))

    :reagent-render
    (fn []
      [:div {:class "flex flex-col items-center justify-center p-8 min-h-full bg-slate-900"}
       [:h1 {:class "text-3xl font-bold text-slate-200 mb-2"} "Snake"]
       [:div {:class "text-slate-400 mb-6"}
        (str "Score: " (:score @state))]

       [:div {:class "relative w-full max-w-[500px]"}
        [board-view]
        [game-over-overlay]]

       [controls]
       
       [:div {:class "mt-8 text-sm text-slate-500"}
        "Use Arrow Keys to move. Space to restart (if implemented)."]])}))

(defn icon-snake []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M19 13.5v4L15.5 21H8.5l-3.5-3.5v-4L8.5 10h7l3.5 3.5z"}] ;; Hexagon shape roughly
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M12 7V3m0 0l-3 3m3-3l3 3"}]]) ;; Arrow

(def plugin
  (plin/plugin
   {:doc "A classic Snake game for the developer tools."
    :deps [iapp/plugin]

    :contributions
    {::iapp/nav-items [{:id :snake
                        :parent-id :dev-tools
                        :label "Snake"
                        :route "snake"
                        :icon icon-snake
                        :component ::ui
                        :order 99}]}

    :beans
    {::ui
     ^{:doc "Main Snake UI."
       :reagent-component true
       :api {:args [] :ret :hiccup}}
     [:= snake-game-component]}}))
