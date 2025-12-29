(ns plinpt.p-breadcrumb.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- Helpers ---

(defn- normalize-hash [h]
  (if (or (str/blank? h) (= h "#")) "/" (str/replace h #"^#" "")))

(defn- match-prefix? [path prefix]
  (cond
    ;; Root path "/" only matches exactly "/"
    (= prefix "/") (= path "/")
    ;; Empty prefix matches nothing
    (str/blank? prefix) false
    ;; Exact match
    (= path prefix) true
    ;; Prefix match (ensure we match at path boundaries)
    :else (let [prefix-with-slash (if (str/ends-with? prefix "/") prefix (str prefix "/"))]
            (str/starts-with? path prefix-with-slash))))

(defn- find-trail-items [tree path]
  "Build the trail by traversing the tree, returns items without Home prepended."
  (loop [current-children tree
         trail []]
    (let [;; Find a child that matches the current path
          match (some (fn [child]
                        (when (and (:full-route child)
                                   (match-prefix? path (:full-route child)))
                          child))
                      current-children)]
      (if match
        (let [new-trail (conj trail {:label (:label match) :path (:full-route match)})]
          (if (and (:children match) 
                   ;; Only continue if we haven't reached the exact match yet
                   (not= path (:full-route match)))
            ;; Continue drilling down
            (recur (:children match) new-trail)
            ;; We've reached the destination or no more children
            new-trail))
        ;; No match found, return what we have
        trail))))

(defn- find-trail [tree path]
  ;; Build the trail by traversing the tree
  (let [home-entry {:label "Home" :path "/"}]
    (if (or (= path "/") (str/blank? path))
      ;; At root, just show Home
      [home-entry]
      ;; Not at root, prepend Home to the trail
      (let [trail-items (find-trail-items tree path)]
        (if (seq trail-items)
          (into [home-entry] trail-items)
          ;; If no trail items found, still show Home
          [home-entry])))))

;; --- State ---

(defonce breadcrumb-state (r/atom {:trail []}))

;; --- Core Logic ---

(defn- update-trail-from-path! [structure path]
  (let [trail (find-trail structure path)]
    (swap! breadcrumb-state assoc :trail trail)))

;; --- Trail Data ---

(defn make-trail-data-atom [structure current-route-atom]
  ;; Create a reactive computation that updates trail when route changes
  (r/track
   (fn []
     (let [current-route (when (and current-route-atom (satisfies? IDeref current-route-atom))
                           @current-route-atom)
           path (or (:path current-route)
                    (normalize-hash (.-hash js/location)))
           trail (find-trail structure path)]
       {:trail trail}))))

;; --- Trail Actions ---

(defn make-trail-actions [navigate!]
  {:navigate! (or navigate! 
                  (fn [path] (set! (.-hash js/location) path)))
   :clear! (fn [])
   :go-back! (fn [] (js/history.back))})

;; --- UI Component ---

(defn breadcrumb-view [trail-data trail-actions]
  (fn []
    (let [{:keys [trail]} (if (and trail-data (satisfies? IDeref trail-data))
                            @trail-data
                            {:trail []})
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
