(ns plinpt.p-breadcrumb.core
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defonce state (r/atom {:trail []}))

(defn- normalize-hash [h]
  (if (or (str/blank? h) (= h "#")) "/" (str/replace h #"^#" "")))

(defn- find-route-label [routes path]
  (let [match (some #(when (= (:path %) path) %) routes)]
    (or (:label match)
        (:title match)
        (let [segment (last (str/split path #"/"))]
          (if (str/blank? segment)
            "Home"
            (str/capitalize segment))))))

(defn update-trail! [routes new-hash]
  (let [path (normalize-hash new-hash)
        label (find-route-label routes path)
        current-trail (:trail @state)
        
        ;; Ensure trail always starts with Home (root)
        trail-with-root (if (empty? current-trail)
                          [{:path "/" :label (find-route-label routes "/")}]
                          current-trail)
        
        existing-index (first (keep-indexed
                               (fn [idx item] (when (= (:path item) path) idx))
                               trail-with-root))]
    (swap! state assoc :trail
           (if existing-index
             (subvec trail-with-root 0 (inc existing-index))
             (conj trail-with-root {:path path :label label})))))

(defn breadcrumb-view [routes]
  (let [handler #(update-trail! routes (.-hash js/location))]
    (.addEventListener js/window "hashchange" handler)
    (handler)
    (fn []
      (let [trail (:trail @state)]
        (when (seq trail)
          [:nav {:class "flex text-sm text-gray-500 px-4 sm:px-6 lg:px-8 py-2 bg-gray-50 border-b border-gray-200"}
           [:ol {:class "list-none p-0 inline-flex flex-wrap"}
            (doall
             (for [[idx item] (map-indexed vector trail)]
               ^{:key (:path item)}
               [:li {:class "flex items-center"}
                (when (pos? idx)
                  [:svg {:class "fill-current w-3 h-3 mx-2 text-gray-400" :viewBox "0 0 320 512"}
                   [:path {:d "M285.476 272.971L91.132 467.314c-9.373 9.373-24.569 9.373-33.941 0l-22.667-22.667c-9.357-9.357-9.375-24.522-.04-33.901L188.505 256 34.484 101.255c-9.335-9.379-9.317-24.544.04-33.901l22.667-22.667c9.373-9.373 24.569-9.373 33.941 0L285.475 239.03c9.373 9.372 9.373 24.568.001 33.941z"}]])
                (if (= idx (dec (count trail)))
                  [:span {:class "font-medium text-gray-800"} (:label item)]
                  [:a {:href (str "#" (:path item))
                       :class "hover:text-blue-600 transition-colors"}
                   (:label item)])]))]])))))
