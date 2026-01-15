(ns plinpt.p-service-invoker-demo.core)

(def is-node?
  (not (nil? (try js/process (catch :default _ nil)))))

(defn get-alasql
  "Lazily retrieves the alasql instance at runtime."
  []
  (if is-node?
    (try (js/require "alasql") (catch :default _ nil))
    (try (.-alasql js/window) (catch :default _ nil))))

(defn demo-invoker [handlers executor endpoint payload]
  (js/Promise.
   (fn [resolve reject]
     (let [handler-entry (some #(when (= (:endpoint %) endpoint) %) handlers)]
       (if handler-entry
         (let [handler-fn (:handler handler-entry)]
           (try
             (let [result (handler-fn executor payload)]
               (if (and result (.-then result))
                 (.then result resolve reject)
                 (resolve result)))
             (catch :default e
               (reject e))))
         (reject (ex-info (str "Unknown endpoint: " endpoint) {:endpoint endpoint})))))))

(defn make-wrapped-executor [api raw-executor]
  (if-let [wrap-fn (:wrap-executor api)]
    (wrap-fn raw-executor)
    raw-executor))

(defn make-wrapped-invoker [api handlers executor]
  (let [wrap-fn (:wrap-invoker api)]
    (if wrap-fn
      (let [wrapped (wrap-fn demo-invoker)]
        (partial wrapped handlers executor))
      (partial demo-invoker handlers executor))))

(defn make-robust-reset [reset-fn]
  (fn []
    (js/console.log "Robust Reset: Dropping auth tables explicitly...")
    (let [tables ["auth_role_permissions" "auth_group_permissions" "auth_group_roles" 
                  "auth_user_roles" "auth_user_groups"
                  "auth_permissions" "auth_roles" "auth_groups" "auth_users"]
          db (get-alasql)]
      (when db
        (doseq [t tables]
          (try
            (db (str "DROP TABLE IF EXISTS " t))
            (catch :default e
              (js/console.warn (str "Failed to drop " t) e))))))
    ;; Call the original reset to handle everything else and re-init
    (reset-fn)))
