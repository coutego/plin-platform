(ns plinpt.p-session.utils)

(defn make-service [invoke collection]
  {:list   (fn [] (invoke :auth/list {:collection collection}))
   :get    (fn [id] (invoke :auth/get {:collection collection :id id}))
   :create (fn [data] (invoke :auth/create {:collection collection :data data}))
   :update (fn [id data] (invoke :auth/update {:collection collection :id id :data data}))
   :delete (fn [id] (invoke :auth/delete {:collection collection :id id}))})
