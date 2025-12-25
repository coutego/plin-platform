(ns showcase.more-automata
  (:require [plin.core :as plin]
            [showcase.cellular-automata :as ca]))

;; --- Logic ---

;; Day & Night
;; B3678 / S34678
;; Known for having complex behavior similar to Game of Life.
(defn step-day-night [state]
  (update state :grid ca/next-gen-life #{3 6 7 8} #{3 4 6 7 8}))

;; Seeds
;; B2 / S (none)
;; All live cells die. Dead cells with exactly 2 neighbors become alive.
;; Very chaotic and explosive.
(defn step-seeds [state]
  (update state :grid ca/next-gen-life #{2} #{}))

;; Maze
;; B3 / S12345
;; Generates maze-like structures.
(defn step-maze [state]
  (update state :grid ca/next-gen-life #{3} #{1 2 3 4 5}))

;; --- Definitions ---

(def day-night-def
  {:id :day-night
   :label "Day & Night"
   :step step-day-night
   :init ca/seed-random
   :extra-actions [{:label "Random Seed" :fn ca/seed-random}]})

(def seeds-def
  {:id :seeds
   :label "Seeds"
   :step step-seeds
   :init ca/seed-empty
   :extra-actions [{:label "Clear (Draw manually)" :fn ca/seed-empty}
                   {:label "Random Seed" :fn ca/seed-random}]})

(def maze-def
  {:id :maze
   :label "Maze"
   :step step-maze
   :init ca/seed-random
   :extra-actions [{:label "Random Seed" :fn ca/seed-random}]})

;; --- Plugin ---

(def plugin
  (plin/plugin
   {:doc "Extra Cellular Automata rules."
    :deps [ca/plugin]
    
    :contributions
    {::ca/automata [day-night-def seeds-def maze-def]}}))
