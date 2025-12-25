(ns plinpt.p-service-invoker-remote.core
  (:require [clojure.string :as str]))

(defn- endpoint->url [endpoint]
  ;; Converts :auth/list -> "/api/auth/list"
  (str "/api/" (namespace endpoint) "/" (name endpoint)))

(defn remote-invoke [endpoint payload]
  (let [url (endpoint->url endpoint)
        opts {:method "POST"
              :headers {"Content-Type" "application/json"}
              :body (js/JSON.stringify (clj->js payload))}]
    (-> (js/fetch url (clj->js opts))
        (.then (fn [resp]
                 (if (.-ok resp)
                   (.json resp)
                   (throw (ex-info "Network Error" {:status (.-status resp)})))))
        (.then (fn [json]
                 (let [result (js->clj json :keywordize-keys true)]
                   (js/console.log "Remote Invoker: Received" (clj->js result))
                   result))))))
