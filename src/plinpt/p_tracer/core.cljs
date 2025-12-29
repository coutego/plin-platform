(ns plinpt.p-tracer.core
  (:require [reagent.core :as r]))

;; --- State ---

(defonce state (r/atom {:enabled? true
                        :active-endpoints #{} ;; Set of strings. If empty, trace all.
                        :traces []            ;; Vector of trace objects
                        :current-trace-id nil}))

;; --- Logic ---

(defn start-trace! [endpoint args]
  (let [id (random-uuid)
        {:keys [enabled? active-endpoints]} @state
        should-trace? (and enabled?
                           (or (empty? active-endpoints)
                               (contains? active-endpoints endpoint)))]
    (when should-trace?
      (swap! state (fn [s]
                     (-> s
                         (assoc :current-trace-id id)
                         (update :traces conj {:id id
                                               :service endpoint
                                               :args args
                                               :start (js/Date.)
                                               :sqls []})))))
    id))

(defn end-trace! [id result]
  (swap! state (fn [s]
                 (cond-> s
                   (= (:current-trace-id s) id) (assoc :current-trace-id nil)
                   true (update :traces (fn [traces]
                                          (mapv (fn [t]
                                                  (if (= (:id t) id)
                                                    (assoc t :end (js/Date.) :result result)
                                                    t))
                                                traces)))))))

(defn log-sql! [sql params]
  (let [{:keys [current-trace-id]} @state]
    (when current-trace-id
      (swap! state update :traces
             (fn [traces]
               (mapv (fn [t]
                       (if (= (:id t) current-trace-id)
                         (update t :sqls conj {:sql sql :params params :time (js/Date.)})
                         t))
                     traces))))))

(defn clear-traces! []
  (swap! state assoc :traces []))

(defn toggle-endpoint! [endpoint]
  (swap! state update :active-endpoints
         #(if (contains? % endpoint) (disj % endpoint) (conj % endpoint))))

;; --- Decorators ---

(defn wrap-invoker [original-invoker]
  (fn [handlers executor endpoint payload]
    (let [trace-id (start-trace! (str endpoint) payload)
          result (original-invoker handlers executor endpoint payload)]
      (if (and result (.-then result))
        (.then result 
               (fn [v] (end-trace! trace-id v) v)
               (fn [e] (end-trace! trace-id e) (throw e)))
        (do (end-trace! trace-id result) result)))))

(defn wrap-executor [original-executor]
  (fn [sql params]
    (log-sql! sql params)
    (original-executor sql params)))
