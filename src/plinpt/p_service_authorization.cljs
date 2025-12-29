(ns plinpt.p-service-authorization
  (:require [plin.core :as plin]
            [plinpt.i-service-authorization :as iservice]
            [plinpt.i-service-invoker :as invoker]
            [plinpt.i-devdoc :as idev]
            [plinpt.i-db :as idb]
            [plinpt.p-session.utils :as utils]
            [plinpt.p-service-authorization.core :as core]))

(def plugin
  (plin/plugin
   {:doc "Implementation plugin providing authorization services via the Service Invoker."
    :deps [iservice/plugin invoker/plugin idev/plugin idb/plugin]
    
    :contributions
    {::idev/plugins [{:id :p-service-authorization
                      :description "Authorization Services."
                      :responsibilities "Registers auth handlers and provides client service beans."
                      :type :feature}]
     
     ;; Register Handlers
     ::invoker/handlers
     [{:endpoint :auth/list   :handler core/list-handler}
      {:endpoint :auth/get    :handler core/get-handler}
      {:endpoint :auth/create :handler core/create-handler}
      {:endpoint :auth/update :handler core/update-handler}
      {:endpoint :auth/delete :handler core/delete-handler}]

     ;; Database Initialization
     ::idb/init
     [{:id :auth-schema
       :schema
       ["CREATE TABLE IF NOT EXISTS auth_users (id STRING, name STRING, PRIMARY KEY (id))"
        "CREATE TABLE IF NOT EXISTS auth_groups (id STRING, description STRING, PRIMARY KEY (id))"
        "CREATE TABLE IF NOT EXISTS auth_roles (id STRING, description STRING, PRIMARY KEY (id))"
        "CREATE TABLE IF NOT EXISTS auth_permissions (id STRING, description STRING, PRIMARY KEY (id))"
        "CREATE TABLE IF NOT EXISTS auth_user_groups (user_id STRING REFERENCES auth_users(id), group_id STRING REFERENCES auth_groups(id), PRIMARY KEY (user_id, group_id))"
        "CREATE TABLE IF NOT EXISTS auth_user_roles (user_id STRING REFERENCES auth_users(id), role_id STRING REFERENCES auth_roles(id), PRIMARY KEY (user_id, role_id))"
        "CREATE TABLE IF NOT EXISTS auth_group_roles (group_id STRING REFERENCES auth_groups(id), role_id STRING REFERENCES auth_roles(id), PRIMARY KEY (group_id, role_id))"
        "CREATE TABLE IF NOT EXISTS auth_group_permissions (group_id STRING REFERENCES auth_groups(id), perm_id STRING REFERENCES auth_permissions(id), PRIMARY KEY (group_id, perm_id))"
        "CREATE TABLE IF NOT EXISTS auth_role_permissions (role_id STRING REFERENCES auth_roles(id), perm_id STRING REFERENCES auth_permissions(id), PRIMARY KEY (role_id, perm_id))"]
       :data
       [;; Permissions
        "DELETE FROM auth_permissions WHERE id IN (':perm/admin', ':perm/view-dashboard', ':perm/edit-plans', ':perm/view-reports', ':perm/manage-users')"
        "INSERT INTO auth_permissions VALUES (':perm/admin', 'Full Access')"
        "INSERT INTO auth_permissions VALUES (':perm/view-dashboard', 'View Dashboard')"
        "INSERT INTO auth_permissions VALUES (':perm/edit-plans', 'Edit Plans')"
        "INSERT INTO auth_permissions VALUES (':perm/view-reports', 'View Reports')"
        "INSERT INTO auth_permissions VALUES (':perm/manage-users', 'Manage Users')"
        
        ;; Roles
        "DELETE FROM auth_roles WHERE id IN (':role/admin', ':role/planner', ':role/viewer')"
        "INSERT INTO auth_roles VALUES (':role/admin', 'Administrator')"
        "INSERT INTO auth_roles VALUES (':role/planner', 'Planner')"
        "INSERT INTO auth_roles VALUES (':role/viewer', 'Viewer')"
        
        ;; Groups
        "DELETE FROM auth_groups WHERE id IN (':group/admins', ':group/staff')"
        "INSERT INTO auth_groups VALUES (':group/admins', 'Admin Group')"
        "INSERT INTO auth_groups VALUES (':group/staff', 'Staff Group')"
        
        ;; Users
        "DELETE FROM auth_users WHERE id IN (':user/admin', ':user/planner', ':user/viewer')"
        "INSERT INTO auth_users VALUES (':user/admin', 'Admin User')"
        "INSERT INTO auth_users VALUES (':user/planner', 'Planner User')"
        "INSERT INTO auth_users VALUES (':user/viewer', 'Viewer User')"
        
        ;; Relations - Clean up first
        "DELETE FROM auth_user_groups WHERE user_id = ':user/admin'"
        "DELETE FROM auth_user_roles WHERE user_id IN (':user/admin', ':user/planner')"
        "DELETE FROM auth_role_permissions WHERE role_id IN (':role/admin', ':role/planner')"

        ;; Relations - Insert
        "INSERT INTO auth_user_groups VALUES (':user/admin', ':group/admins')"
        "INSERT INTO auth_user_roles VALUES (':user/admin', ':role/admin')"
        "INSERT INTO auth_role_permissions VALUES (':role/admin', ':perm/admin')"
        
        "INSERT INTO auth_user_roles VALUES (':user/planner', ':role/planner')"
        "INSERT INTO auth_role_permissions VALUES (':role/planner', ':perm/view-dashboard')"
        "INSERT INTO auth_role_permissions VALUES (':role/planner', ':perm/edit-plans')"
        "INSERT INTO auth_role_permissions VALUES (':role/planner', ':perm/view-reports')"]}]}

    :beans
    {;; Client Service Beans
     ::iservice/user-service
     ^{:doc "Service for managing users via remote/local invoker." :api {:ret :map}}
     [(fn [inv] (utils/make-service inv :users)) ::invoker/invoke]

     ::iservice/group-service
     ^{:doc "Service for managing groups via remote/local invoker." :api {:ret :map}}
     [(fn [inv] (utils/make-service inv :groups)) ::invoker/invoke]

     ::iservice/role-service
     ^{:doc "Service for managing roles via remote/local invoker." :api {:ret :map}}
     [(fn [inv] (utils/make-service inv :roles)) ::invoker/invoke]

     ::iservice/permission-service
     ^{:doc "Service for managing permissions via remote/local invoker." :api {:ret :map}}
     [(fn [inv] (utils/make-service inv :permissions)) ::invoker/invoke]}}))
