(ns plinpt.p-hash-router.core
  "Hash-based router implementation.
   
   Handles browser hash changes and provides reactive routing state."
  (:require [reagent.core :as r]
            [clojure.string :as str]))

;; --- State ---

(defonce current-route (r/atom nil))
(defonce active-routes (atom []))
(defonce homepage-path (atom "/"))
(defonce initialized? (atom false))

;; --- Helpers ---

(defn- normalize-hash [hash]
  "Normalizes a hash string to a clean path."
  (if (or (str/blank? hash) (= hash "#"))
    "/"
    (str/replace hash #"^#" "")))

(defn- extract-params [path-parts def-parts]
  "Extracts route parameters from path parts based on definition parts.
   E.g., path '/users/123' with def '/users/:id' returns {:id '123'}"
  (reduce (fn [params [p d]]
            (if (str/starts-with? d ":")
              (assoc params (keyword (subs d 1)) p)
              params))
          {}
          (map vector path-parts def-parts)))

(defn match-route
  "Matches a path against a list of route definitions.
   Supports exact matches and parameterized routes (e.g., /users/:id).
   Returns the matched route map with :params added, or nil."
  [route-defs path]
  (some (fn [route-def]
          (let [def-path (:path route-def)]
            (when def-path
              (if (str/includes? def-path ":")
                ;; Handle parameterized routes
                (let [path-parts (str/split path #"/")
                      def-parts (str/split def-path #"/")]
                  (when (= (count path-parts) (count def-parts))
                    (when (every? (fn [[p d]]
                                    (or (= p d)
                                        (str/starts-with? d ":")))
                                  (map vector path-parts def-parts))
                      (assoc route-def :params (extract-params path-parts def-parts)))))
                ;; Handle exact matches
                (when (= def-path path)
                  (assoc route-def :params {}))))))
        route-defs))

(defn- handle-hash-change
  "Handler for browser hash change events.
   Updates current-route atom based on the new hash."
  []
  (let [raw-hash (.-hash js/location)
        path (normalize-hash raw-hash)
        home @homepage-path
        routes @active-routes]
    
    ;; Redirect to homepage if at root and homepage is not root
    (if (and (or (str/blank? raw-hash) (= raw-hash "#") (= raw-hash "#/"))
             (not= home "/"))
      (set! (.-hash js/location) home)
      
      ;; Otherwise match route
      (let [matched (match-route routes path)]
        (reset! current-route matched)))))

(defn navigate!
  "Navigates to the specified path by updating the browser hash."
  [path]
  (when path
    (set! (.-hash js/location) path)))

(defn setup!
  "Initializes the router with homepage path.
   Routes are set separately via set-routes!.
   Sets up the hash change listener and performs initial routing."
  [home]
  (when-not @initialized?
    (reset! homepage-path (or home "/"))
    
    ;; Set up hash change listener
    (set! (.-onhashchange js/window) handle-hash-change)
    
    ;; Perform initial routing
    (handle-hash-change)
    
    (reset! initialized? true)))

(defn set-routes!
  "Sets the active routes. Called during container initialization."
  [routes]
  (let [valid-routes (->> routes
                          (filter map?)
                          (filter :path)
                          (filter :component)
                          vec)]
    (reset! active-routes valid-routes)
    ;; Re-run routing if already initialized
    (when @initialized?
      (handle-hash-change))))

(defn make-match-route-fn
  "Creates a match-route function bound to the current routes."
  []
  (fn [path]
    (match-route @active-routes path)))

(defn make-setup-fn
  "Creates a setup function that uses injected routes."
  [routes]
  ;; Set routes immediately when this function is called during container build
  (set-routes! routes)
  ;; Return the setup function that will be called later
  (fn [home]
    (setup! home)))
