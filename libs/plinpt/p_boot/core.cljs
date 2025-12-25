(ns plinpt.p-boot.core
  (:require [reagent.core :as r]
            [plin.core :as pi]))

;; 1. The Master State
(defonce state (r/atom {:all-plugins []     ;; Initial list of plugins
                        :disabled-ids #{}   ;; IDs selected by user to disable
                        :container nil      ;; The current Injectable container
                        :last-error nil}))

;; 2. Dependency Resolution Logic
(defn get-cascading-disabled
  "If Plugin A is disabled, and Plugin B depends on A, B must also be disabled."
  [plugins disabled-ids]

  (loop [current-disabled disabled-ids]
    (let [newly-disabled 
          (reduce
           (fn [acc plugin]
             (if (and (not (contains? current-disabled (:id plugin)))
                      (some #(contains? current-disabled %) (:deps plugin)))
               (conj acc (:id plugin))
               acc))
           #{}
           plugins)]
      (if (empty? newly-disabled)
        current-disabled
        (recur (into current-disabled newly-disabled))))))

;; 3. The Reload Logic
(defn reload! []
  (js/setTimeout
   (fn []
     (println "System: Reloading...")
     (try
       (let [{:keys [all-plugins disabled-ids]} @state
             
             ;; Calculate which plugins to actually load
             final-disabled (get-cascading-disabled all-plugins disabled-ids)
             plugins-to-load (filter #(not (contains? final-disabled (:id %))) all-plugins)
             
             ;; Create Container
             ;; p-boot is already in 'all-plugins', so it gets loaded and exposes ::api
             container (pi/load-plugins (vec plugins-to-load))
             
             ;; Resolve the mount function.
             ;; We use the keyword directly to avoid hard dependency on p-app-shell in this namespace
             mount-fn (:plinpt.p-app-shell/mount container)]
         
         ;; Update State
         (swap! state assoc :container container :last-error nil)
         
         ;; Mount App
         (if mount-fn
           (mount-fn)
           (println "WARNING: No :plinpt.p-app-shell/mount bean found."))
         
         (println "System: Reload complete. Active plugins:" (count plugins-to-load)))
       (catch :default e
         (js/console.error e)
         (swap! state assoc :last-error e))))
   0))

;; 4. Initialization
(defn init [plugins]
  (swap! state assoc :all-plugins plugins)
  (reload!))
