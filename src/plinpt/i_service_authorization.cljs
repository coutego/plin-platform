(ns plinpt.i-service-authorization
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface plugin for authorization services, defining async CRUD operations for users, groups, roles, and permissions."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :iservice-authorization
                      :description "Interface for Authorization Services."
                      :responsibilities "Defines contracts for user/group/role/permission services."
                      :type :infrastructure}]}

    :beans
    {::user-service
     ^{:doc "Service for managing users. Map of async functions: {:list, :get, :create, :update, :delete}"
       :api {:ret :map}}
     [:= {:list   (fn [] (js/Promise.resolve []))
          :get    (fn [id] (js/Promise.resolve nil))
          :create (fn [data] (js/Promise.resolve data))
          :update (fn [id data] (js/Promise.resolve data))
          :delete (fn [id] (js/Promise.resolve true))}]

     ::group-service
     ^{:doc "Service for managing groups. Map of async functions."
       :api {:ret :map}}
     [:= {:list   (fn [] (js/Promise.resolve []))
          :get    (fn [id] (js/Promise.resolve nil))
          :create (fn [data] (js/Promise.resolve data))
          :update (fn [id data] (js/Promise.resolve data))
          :delete (fn [id] (js/Promise.resolve true))}]

     ::role-service
     ^{:doc "Service for managing roles. Map of async functions."
       :api {:ret :map}}
     [:= {:list   (fn [] (js/Promise.resolve []))
          :get    (fn [id] (js/Promise.resolve nil))
          :create (fn [data] (js/Promise.resolve data))
          :update (fn [id data] (js/Promise.resolve data))
          :delete (fn [id] (js/Promise.resolve true))}]

     ::permission-service
     ^{:doc "Service for managing permissions. Map of async functions."
       :api {:ret :map}}
     [:= {:list   (fn [] (js/Promise.resolve []))
          :get    (fn [id] (js/Promise.resolve nil))
          :create (fn [data] (js/Promise.resolve data))
          :update (fn [id data] (js/Promise.resolve data))
          :delete (fn [id] (js/Promise.resolve true))}]}}))
