(ns plinpt.p-game-2048.core
  (:require [reagent.core :as r]))

;; --- Game Logic ---

(defn empty-board []
  (vec (repeat 4 (vec (repeat 4 0)))))

(defn get-empty-cells [board]
  (for [r (range 4)
        c (range 4)
        :when (zero? (get-in board [r c]))]
    [r c]))

(defn add-random-tile [board]
  (let [empty-cells (get-empty-cells board)]
    (if (empty? empty-cells)
      board
      (let [[r c] (rand-nth empty-cells)
            val (if (< (rand) 0.9) 2 4)]
        (assoc-in board [r c] val)))))

(defn merge-row [row]
  (let [non-zeros (filterv pos? row)
        merged (loop [remaining non-zeros
                      acc []]
                 (cond
                   (empty? remaining) acc
                   (empty? (rest remaining)) (conj acc (first remaining))
                   (= (first remaining) (second remaining)) (recur (drop 2 remaining) (conj acc (* 2 (first remaining))))
                   :else (recur (rest remaining) (conj acc (first remaining)))))
        padded (into merged (repeat (- 4 (count merged)) 0))]
    padded))

(defn transpose [board]
  (apply mapv vector board))

(defn move-left [board]
  (mapv merge-row board))

(defn move-right [board]
  (mapv (comp vec reverse merge-row reverse) board))

(defn move-up [board]
  (transpose (move-left (transpose board))))

(defn move-down [board]
  (transpose (move-right (transpose board))))

(defn game-over? [board]
  (and (empty? (get-empty-cells board))
       (every? (fn [r] (= r (merge-row r))) board)
       (every? (fn [r] (= r (merge-row r))) (transpose board))))

;; --- State ---

(defonce game-state (r/atom {:board (add-random-tile (add-random-tile (empty-board)))
                             :score 0
                             :game-over false}))

(defn reset-game! []
  (reset! game-state {:board (add-random-tile (add-random-tile (empty-board)))
                      :score 0
                      :game-over false}))

(defn handle-move! [direction-fn]
  (let [old-board (:board @game-state)
        new-board (direction-fn old-board)]
    (when (not= old-board new-board)
      (let [board-with-tile (add-random-tile new-board)]
        (swap! game-state assoc :board board-with-tile)
        (when (game-over? board-with-tile)
          (swap! game-state assoc :game-over true))))))

(defn handle-keydown [e]
  (when (contains? #{"ArrowUp" "ArrowDown" "ArrowLeft" "ArrowRight"} (.-key e))
    (.preventDefault e)
    (case (.-key e)
      "ArrowUp" (handle-move! move-up)
      "ArrowDown" (handle-move! move-down)
      "ArrowLeft" (handle-move! move-left)
      "ArrowRight" (handle-move! move-right)
      nil)))

;; --- Components ---

(defn tile-color [val]
  (case val
    2 "bg-white text-gray-700 border border-gray-200"
    4 "bg-orange-100 text-gray-800 border border-orange-200"
    8 "bg-orange-300 text-white border-orange-400"
    16 "bg-orange-400 text-white border-orange-500"
    32 "bg-orange-500 text-white border-orange-600"
    64 "bg-orange-600 text-white border-orange-700"
    128 "bg-yellow-400 text-white border-yellow-500"
    256 "bg-yellow-500 text-white border-yellow-600"
    512 "bg-yellow-600 text-white border-yellow-700"
    1024 "bg-yellow-700 text-white border-yellow-800"
    2048 "bg-yellow-800 text-white border-yellow-900"
    "bg-gray-900 text-white"))

(def game-board
  (r/create-class
   {:component-did-mount
    (fn [] (.addEventListener js/window "keydown" handle-keydown))
    :component-will-unmount
    (fn [] (.removeEventListener js/window "keydown" handle-keydown))
    :reagent-render
    (fn []
      (let [{:keys [board game-over]} @game-state]
        [:div {:class "flex flex-col items-center justify-center p-8 min-h-[80vh]"}
         [:div {:class "mb-8 flex justify-between items-center w-full max-w-md"}
          [:h1 {:class "text-4xl font-bold text-gray-800"} "2048"]
          [:button {:class "px-6 py-2 bg-blue-600 text-white rounded-lg shadow hover:bg-blue-700 transition-colors"
                    :on-click reset-game!} "New Game"]]
         
         ;; Game Container
         [:div {:class "relative bg-gray-300 p-4 rounded-xl shadow-xl w-full max-w-md aspect-square"}
          
          ;; Game Over Overlay
          (when game-over
            [:div {:class "absolute inset-0 bg-white bg-opacity-80 flex flex-col items-center justify-center rounded-xl z-20 backdrop-blur-sm transition-opacity duration-500"}
             [:h2 {:class "text-4xl font-bold text-gray-800 mb-6"} "Game Over!"]
             [:button {:class "px-8 py-3 bg-blue-600 text-white rounded-lg font-bold text-lg shadow-lg hover:bg-blue-700 transform hover:scale-105 transition-all"
                       :on-click reset-game!} "Try Again"]])
          
          ;; Grid
          [:div {:class "grid grid-cols-4 gap-3 h-full"}
           (for [r (range 4)
                 c (range 4)]
             (let [val (get-in board [r c])]
               ^{:key (str r "-" c)}
               [:div {:class "relative w-full h-full"}
                ;; Background cell
                [:div {:class "absolute inset-0 bg-gray-400 rounded-lg opacity-30"}]
                
                ;; Tile
                (when (pos? val)
                  [:div {:class (str "absolute inset-0 flex items-center justify-center rounded-lg font-bold text-3xl shadow-sm "
                                     (tile-color val)
                                     " transition-all duration-200 ease-in-out transform scale-100 animate-pop")
                         :style {:animation "pop 0.2s ease-in-out"}}
                   val])]))]]
         
         [:div {:class "mt-8 text-gray-600 text-lg font-medium"}
          "Use arrow keys to join the numbers and get to the 2048 tile!"]
         
         ;; CSS for animation
         [:style
          "@keyframes pop {
             0% { transform: scale(0); opacity: 0; }
             50% { transform: scale(1.1); }
             100% { transform: scale(1); opacity: 1; }
           }"]]))}))

(defn icon-game []
  [:svg {:class "h-6 w-6 text-white" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"}]
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" :d "M21 12a9 9 0 11-18 0 9 9 0 0118 0z"}]])
