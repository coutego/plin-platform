(ns plinpt.p-service-invoker-remote
  (:require [plin.core :as plin]
            [plinpt.i-service-invoker :as invoker]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.p-service-invoker-remote.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Remote implementation of the Service Invoker using Fetch API."
    :deps [invoker/plugin idev/plugin i-tracer/plugin]

    :contributions
    {::idev/plugins [{:id :p-service-invoker-remote
                      :description "Remote Service Invoker."
                      :responsibilities "Executes handlers via HTTP Fetch against /api."
                      :type :infrastructure}]}

    :beans
    {;; We override the invoke bean with the fetch implementation, wrapped with tracing
     ::invoker/invoke
     ^{:doc "Remote implementation of the service invoker."
       :api {:args [["endpoint" :auth/list :keyword] ["payload" {} :map]] :ret "Promise<any>"}}
     [(fn [api]
        (let [wrap-fn (:wrap-invoker api)]
          (if wrap-fn
            (let [wrapped (wrap-fn (fn [_ _ endpoint payload] (core/remote-invoke endpoint payload)))]
              ;; The wrapped function expects (handlers executor endpoint payload)
              ;; But remote-invoke doesn't use handlers/executor.
              ;; So we create an adapter that ignores the first two args.
              (fn [endpoint payload]
                (wrapped nil nil endpoint payload)))
            core/remote-invoke)))
      ::i-tracer/api]

     ;; In remote mode, db-executor is usually not available on the client
     ::invoker/db-executor
     [:= (fn [_ _] (js/Promise.reject "Direct DB access not allowed in Client-Server mode."))]}}))
