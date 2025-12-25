(ns showcase.cellular-automata
  (:require [reagent.core :as r]
            [plin.core :as plin]
            [plinpt.i-devtools :as devtools]
            [plinpt.i-app-shell :as shell]))

;; --- Constants & Config ---

(def rows 60)
(def cols 80)
(def cell-size 5) ;; Reduced size for finer grid

;; --- Logic: Common ---

(defn create-empty-grid []
  (vec (repeat rows (vec (repeat cols 0)))))

(defn get-neighbors [grid r c]
  (let [deltas [[-1 -1] [-1 0] [-1 1]
                [0 -1]         [0 1]
                [1 -1] [1 0] [1 1]]]
    (reduce (fn [count [dr dc]]
              (let [nr (+ r dr)
                    nc (+ c dc)]
                (if (and (>= nr 0) (< nr rows)
                         (>= nc 0) (< nc cols)
                         (pos? (get-in grid [nr nc])))
                  (inc count)
                  count)))
            0
            deltas)))

;; --- Logic: Game of Life & HighLife ---

(defn next-gen-life [grid rule-b rule-s]
  (vec (map-indexed 
        (fn [r row]
          (vec (map-indexed 
                (fn [c cell]
                  (let [n (get-neighbors grid r c)]
                    (if (pos? cell)
                      (if (contains? rule-s n) 1 0)
                      (if (contains? rule-b n) 1 0))))
                row)))
        grid)))

(defn step-gol [state]
  (update state :grid next-gen-life #{3} #{2 3}))

(defn step-highlife [state]
  (update state :grid next-gen-life #{3 6} #{2 3}))

;; --- Logic: Langton's Ant ---

(defn step-ant [state]
  (let [{:keys [grid ant]} state
        {:keys [x y dir]} ant
        
        ;; Current cell color (0 or 1)
        current-color (get-in grid [y x])
        
        ;; 1. Turn: Right (1) if 0, Left (-1) if 1
        ;; N(0) -> E(1) -> S(2) -> W(3)
        turn (if (zero? current-color) 1 -1)
        new-dir (mod (+ dir turn) 4)
        
        ;; 2. Flip color
        new-color (if (zero? current-color) 1 0)
        new-grid (assoc-in grid [y x] new-color)
        
        ;; 3. Move
        [dx dy] (case new-dir
                  0 [0 -1] ;; N
                  1 [1 0]  ;; E
                  2 [0 1]  ;; S
                  3 [-1 0] ;; W
                  )
        
        new-x (mod (+ x dx) cols)
        new-y (mod (+ y dy) rows)]
    
    (-> state
        (assoc :grid new-grid)
        (assoc :ant {:x new-x :y new-y :dir new-dir}))))

;; --- Init Functions ---

(defn seed-empty [state]
  (assoc state 
         :grid (create-empty-grid)
         :generation 0
         :ant {:x (quot cols 2) :y (quot rows 2) :dir 0}))

(defn seed-random [state]
  (let [new-grid (vec (for [_ (range rows)]
                        (vec (for [_ (range cols)]
                               (if (> (Math/random) 0.8) 1 0)))))]
    (assoc (seed-empty state) :grid new-grid)))

(defn seed-replicator [state]
  (let [base (seed-empty state)
        cx (quot cols 2)
        cy (quot rows 2)
        points [[0 2] [0 3] [0 4]
                [1 1] [1 4]
                [2 0] [2 4]
                [3 0] [3 4]
                [4 0] [4 1] [4 2]]]
    (update base :grid 
            (fn [g]
              (reduce (fn [acc [dy dx]]
                        (assoc-in acc [(+ cy dy -2) (+ cx dx -2)] 1))
                      g points)))))

;; --- Drawing Helpers ---

(defn draw-ant [ctx state cell-size]
  (set! (.-fillStyle ctx) "#f43f5e") ;; Rose-500
  (let [{:keys [x y]} (:ant state)]
    (.fillRect ctx (* x cell-size) (* y cell-size) (dec cell-size) (dec cell-size))))

;; --- Automata Definitions ---

(def gol-def
  {:id :gol
   :label "Game of Life"
   :step step-gol
   :init seed-random
   :extra-actions [{:label "Random Seed" :fn seed-random}]})

(def highlife-def
  {:id :highlife
   :label "HighLife (Replicator)"
   :step step-highlife
   :init seed-random
   :extra-actions [{:label "Random Seed" :fn seed-random}
                   {:label "Spawn Replicator" :fn seed-replicator}]})

(def ant-def
  {:id :ant
   :label "Langton's Ant"
   :step step-ant
   :init seed-empty
   :draw-extra draw-ant
   :extra-actions [{:label "Reset Center" :fn seed-empty}]})

(def default-automata [gol-def highlife-def ant-def])

;; --- State ---

(defonce app-state 
  (r/atom (-> {:grid (create-empty-grid)
               :running? false
               :mode :gol
               :dark-mode? false
               :step-fn step-gol
               :init-fn seed-random
               :draw-extra-fn nil
               :extra-actions (:extra-actions gol-def)
               :ant {:x (quot cols 2) :y (quot rows 2) :dir 0}
               :generation 0
               :speed 100}
              (seed-random))))

(def timer-ref (atom nil))

;; --- Loop ---

(defn tick []
  (let [{:keys [running? step-fn]} @app-state]
    (when (and running? step-fn)
      (swap! app-state update :generation inc)
      (swap! app-state step-fn)
      (reset! timer-ref (js/setTimeout tick (:speed @app-state))))))

(defn toggle-running []
  (let [running? (:running? @app-state)]
    (if running?
      (do (js/clearTimeout @timer-ref)
          (swap! app-state assoc :running? false))
      (do (swap! app-state assoc :running? true)
          (tick)))))

(defn reset-game []
  (js/clearTimeout @timer-ref)
  (swap! app-state assoc :running? false)
  (let [init-fn (:init-fn @app-state)]
    (when init-fn
      (swap! app-state init-fn))))

(defn switch-mode [automaton-def]
  (js/clearTimeout @timer-ref)
  (swap! app-state assoc 
         :mode (:id automaton-def)
         :step-fn (:step automaton-def)
         :init-fn (:init automaton-def)
         :draw-extra-fn (:draw-extra automaton-def)
         :extra-actions (:extra-actions automaton-def)
         :running? false)
  ;; Initialize
  (when-let [init (:init automaton-def)]
    (swap! app-state init)))

;; --- Components ---

(defn icon-automata []
  [:svg {:class "w-6 h-6" :fill "none" :stroke "currentColor" :viewBox "0 0 24 24"}
   [:path {:stroke-linecap "round" :stroke-linejoin "round" :stroke-width "2" 
           :d "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"}]])

(defn draw [canvas]
  (let [ctx (.getContext canvas "2d")
        state @app-state
        {:keys [grid draw-extra-fn dark-mode?]} state
        
        bg-color (if dark-mode? "#1e293b" "#f8fafc") ;; Slate-800 vs Slate-50
        cell-color (if dark-mode? "#38bdf8" "#0ea5e9")] ;; Sky-400 vs Sky-500
          
    ;; Clear
    (set! (.-fillStyle ctx) bg-color)
    (.fillRect ctx 0 0 (.-width canvas) (.-height canvas))
    
    ;; Draw Cells
    (set! (.-fillStyle ctx) cell-color)
    (doseq [r (range rows)
            c (range cols)]
      (when (pos? (get-in grid [r c]))
        (.fillRect ctx (* c cell-size) (* r cell-size) (dec cell-size) (dec cell-size))))
    
    ;; Draw Extra (e.g. Ant)
    (when draw-extra-fn
      (draw-extra-fn ctx state cell-size))))

(defn canvas-view []
  (let [canvas-ref (r/atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (when @canvas-ref (draw @canvas-ref)))
      
      :component-did-update
      (fn [this]
        (when @canvas-ref (draw @canvas-ref)))
      
      :reagent-render
      (fn []
        @app-state ;; Subscribe
        [:canvas {:width (* cols cell-size)
                  :height (* rows cell-size)
                  :class "border border-slate-300 dark:border-slate-700 rounded shadow-lg cursor-pointer"
                  :ref (fn [el] (reset! canvas-ref el))
                  :on-click (fn [e]
                              (let [rect (.getBoundingClientRect (.-target e))
                                    x (- (.-clientX e) (.-left rect))
                                    y (- (.-clientY e) (.-top rect))
                                    c (int (/ x cell-size))
                                    r (int (/ y cell-size))]
                                (swap! app-state update-in [:grid r c] #(if (zero? %) 1 0))))}])})))

(defn controls [automata-list]
  (let [{:keys [running? mode generation speed extra-actions dark-mode?]} @app-state]
    [:div {:class "flex flex-col gap-4 mb-6 p-4 bg-white rounded-lg shadow"}
     [:div {:class "flex flex-wrap items-center gap-4"}
      ;; Mode Select
      [:select {:class "px-3 py-2 border rounded bg-gray-50"
                :value (name mode)
                :on-change (fn [e]
                             (let [val (.. e -target -value)
                                   def (some #(when (= (name (:id %)) val) %) automata-list)]
                               (when def (switch-mode def))))}
       (for [a automata-list]
         ^{:key (:id a)}
         [:option {:value (name (:id a))} (:label a)])]
      
      ;; Play/Pause
      [:button {:class (str "px-4 py-2 rounded font-bold text-white transition-colors "
                            (if running? "bg-amber-500 hover:bg-amber-600" "bg-green-500 hover:bg-green-600"))
                :on-click toggle-running}
       (if running? "Pause" "Start")]
      
      ;; Reset
      [:button {:class "px-4 py-2 rounded bg-gray-200 hover:bg-gray-300 font-medium"
                :on-click reset-game}
       "Reset"]
      
      ;; Extra Actions (Dynamic)
      (for [[idx action] (map-indexed vector extra-actions)]
        ^{:key idx}
        [:button {:class "px-4 py-2 rounded bg-indigo-100 text-indigo-700 hover:bg-indigo-200 font-medium"
                  :on-click #(do (js/clearTimeout @timer-ref)
                                 (swap! app-state assoc :running? false)
                                 (swap! app-state (:fn action)))}
         (:label action)])
      
      ;; Dark Mode Toggle
      [:label {:class "flex items-center gap-2 cursor-pointer select-none"}
       [:input {:type "checkbox"
                :checked dark-mode?
                :on-change #(swap! app-state update :dark-mode? not)}]
       [:span {:class "text-sm font-medium text-gray-700"} "Dark Mode"]]
      
      ;; Speed
      [:div {:class "flex items-center justify-between w-full"} ;; 1. w-full + justify-between

       ;; Left Side
       [:div {:class "text-sm text-gray-500 font-mono"}
        (str "Generation: " generation)]

       ;; Right Side (Wrapped in a div to stay together)
       [:div {:class "flex items-center gap-2"}
        [:span {:class "text-sm text-gray-500"} "Speed:"]

        [:input {:type "range" :min "10" :max "500" :step "10"
                 :value (- 510 speed)
                 :on-change #(swap! app-state assoc :speed (- 510 (.. % -target -value)))}]]]]]))

(defn page [automata-list]
  [:div {:class "p-6 max-w-5xl mx-auto"}
   [:h1 {:class "text-3xl font-bold text-slate-800 mb-2"} "Cellular Automata"]
   [:p {:class "text-slate-600 mb-6"} 
    "A showcase of different cellular automata rules running in the browser."]
   
   [controls automata-list]
   
   [:div {:class "flex justify-center overflow-auto"}
    [canvas-view]]
   
   [:div {:class "mt-6 text-sm text-gray-500"}
    [:p "Click on the grid to toggle cells manually."]]])

;; --- Plugin Definition ---

(def plugin
  (plin/plugin
   {:doc "Cellular Automata Showcase."
    :deps [devtools/plugin shell/plugin]
    
    :extensions
    [{:key ::automata
      :handler (plin/collect-data ::automata)
      :doc "List of automata definitions. Schema: {:id :keyword :label string :step fn :init fn :draw-extra fn :extra-actions vector}"}]

    :contributions
    {::devtools/items [{:title "Cellular Automata"
                        :description "Game of Life, HighLife, and Langton's Ant."
                        :icon icon-automata
                        :color-class "bg-indigo-500"
                        :href "/showcase/automata"
                        :order 50}]
     
     ;; Contribute the built-in automata to our own extension point
     ::automata default-automata
     
     ;; Register the route, injecting the collected automata list
     ::shell/routes [::route]}
    
    :beans
    {;; Default empty list for the extension point (will be populated by contributions)
     ::automata [:= []]
     
     ;; Route bean that injects the automata list
     ::route
     ^{:doc "Route definition for Cellular Automata page."
       :api {:ret :map}}
     [(fn [automata]
        {:path "/showcase/automata"
         :component (partial page automata)})
      ::automata]}}))
