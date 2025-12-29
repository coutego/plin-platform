(ns plinpt.p-breadcrumb.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- Helpers ---

(defn- normalize-hash [h]
  (if (or (str/blank? h) (= h "#")) "/" (str/replace h #"^#" "")))

(defn- match-prefix? [path prefix]
  (or (= path prefix)
      (str/starts-with? path (if (str/ends-with? prefix "/") prefix (str prefix "/")))))

(defn- find-trail [tree path]
  (let [virtual-root {:id :root :label "Home" :children tree :full-route "/"}]
    (loop [current virtual-root
           trail []]
      (let [new-trail (conj trail {:label (:label current) :path (:full-route current)})
            children (:children current)
            match (some (fn [child]
                          (when (match-prefix? path (:full-route child))
                            child))
                        children)]
        (if match
          (recur match new-trail)
          new-trail)))))

;; --- State ---

(defonce breadcrumb-state (r/atom {:trail []}))

;; --- Core Logic ---

(defn update-trail! [structure]
  (let [path (normalize-hash (.-hash js/location))
        trail (find-trail structure path)]
    (swap! breadcrumb-state assoc :trail trail)))

;; --- Setup ---

(defn setup-hash-listener! [structure]
  (let [handler #(update-trail! structure)]
    (.addEventListener js/window "hashchange" handler)
    (handler)))

;; --- Trail Data ---

(defn make-trail-data-atom [structure]
  (setup-hash-listener! structure)
  (r/track (fn [] @breadcrumb-state)))

;; --- Trail Actions ---

(defn navigate-to! [path]
  (set! (.-hash js/location) path))

(defn make-trail-actions []
  {:navigate! navigate-to!
   :clear! (fn [])
   :go-back! (fn [] (js/history.back))})

;; --- UI Component ---

(defn breadcrumb-view [trail-data trail-actions]
  (fn []
    (let [{:keys [trail]} @trail-data
          {:keys [navigate!]} trail-actions]
      (when (seq trail)
        [:nav {:class "flex text-xs text-slate-400 font-medium mb-4" :aria-label "Breadcrumb"}
         [:ol {:class "inline-flex items-center space-x-2"}
          (doall
           (for [[idx item] (map-indexed vector trail)]
             ^{:key (str (:path item) "-" idx)}
             [:li {:class "inline-flex items-center"}
              (when (pos? idx)
                [:span {:class "mx-1 text-slate-300"} "/"])
              (if (= idx (dec (count trail)))
                [:span {:class "text-slate-700 font-semibold"} (:label item)]
                [:a {:href (str "#" (:path item))
                     :on-click (fn [e]
                                 (.preventDefault e)
                                 (navigate! (:path item)))
                     :class "hover:text-ec-blue transition-colors"}
                 (:label item)])]))]]))))

(defn breadcrumb-initializer [trail-data trail-actions]
  (fn []
    [breadcrumb-view trail-data trail-actions]))
