(ns plinpt-extras.showcase.snake
  (:require [plin.core :as plin]
            [reagent.core :as r]
            [plinpt.i-application :as iapp]))

;; --- Game Constants & Logic ---

(def board-width 20)
(def board-height 20)
(def initial-snake [[10 10] [10 11] [10 12]])
(def initial-dir [0 -1])
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
      (or (< (first new-head) 0)
          (>= (first new-head) board-width)
          (< (second new-head) 0)
          (>= (second new-head) board-height)
          (some #{new-head} (drop-last snake)))
      (assoc g :game-over? true)

      (= new-head food)
      (-> g
          (update :snake #(cons new-head %))
          (update :score inc)
          (assoc :food (rand-pos)))

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
                                  is-head? "#4ade80"
                                  is-snake? "#86efac"
                                  is-food? "#f87171"
                                  :else "#1e293b")
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
              :background-color "#334155"
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
    [:div {:class "absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/70 backdrop-blur-sm rounded-lg"}
     [:h2 {:class "text-4xl text-red-500 font-bold mb-4"} "Game Over"]
     [:div {:class "text-white text-xl mb-6"} (str "Score: " (:score @state))]
     [:button
      {:class "px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-bold rounded-lg shadow-lg transition transform hover:scale-105"
       :on-click #(reset! state (new-game))}
      "Play Again"]]))

(def snake-game-component
  (r/create-class
   {:component-did-mount
    (fn []
      (reset! state (new-game))
      (js/window.addEventListener "keydown"
        (fn [e]
          (let [k (.-key e)]
            (when (#{"ArrowUp" "ArrowDown" "ArrowLeft" "ArrowRight"} k)
              (.preventDefault e)
              (swap! state update :direction #(or (key->dir k %) %))))))
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
        "Use Arrow Keys to move."]])}))

;; --- Icon ---

(def icon-snake
  [:svg {:class "w-5 h-5" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2"
           :d "M4 8h4l2-2h4l2 2h4M6 12h12M8 16h8"}]])

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "A classic Snake game showcase."
    :deps [iapp/plugin]

    :contributions
    {::iapp/nav-items [{:id :snake
                        :parent-id :development
                        :label "Snake"
                        :description "Classic snake game"
                        :route "snake"
                        :icon icon-snake
                        :icon-color "text-green-600 bg-green-50"
                        :component snake-game-component
                        :order 51}]}}))
