(ns plinpt.p-service-authorization.core
  (:require [clojure.string :as str]))

;; --- Helpers ---

(defn- kw->str [k] (if (keyword? k) (str k) k))

(defn- str->kw [s] 
  (cond
    (keyword? s) s
    (and (string? s) (str/starts-with? s ":")) (keyword (subs s 1))
    :else (keyword s)))

(defn- ensure-kw [v]
  (if (string? v) (keyword v) v))

(defn- fetch-relations [executor id table-name id-col target-col]
  (-> (executor (str "SELECT " target-col " as val FROM " table-name " WHERE " id-col " = ?") [id])
      (.then (fn [rows] (set (map (comp str->kw :val) rows))))))

(defn- fetch-permissions-for-roles [executor roles]
  (if (empty? roles)
    (js/Promise.resolve #{})
    (let [role-strs (map kw->str roles)
          ;; Construct IN clause manually for simplicity in this demo
          in-clause (str/join ", " (map #(str "'" % "'") role-strs))]
      (-> (executor (str "SELECT perm_id FROM auth_role_permissions WHERE role_id IN (" in-clause ")") [])
          (.then (fn [rows] (set (map (comp str->kw :perm_id) rows))))))))

(defn- update-relations [executor id table-name id-col target-col new-values]
  (-> (executor (str "DELETE FROM " table-name " WHERE " id-col " = ?") [id])
      (.then (fn []
               (let [vals (vec new-values)]
                 (when (seq vals)
                   (js/Promise.all
                    (map #(executor (str "INSERT INTO " table-name " VALUES (?, ?)") [id (kw->str %)]) vals))))))))

;; --- Handlers ---

(defn list-handler [executor {collection :collection}]
  (case (ensure-kw collection)
    :users
    (-> (executor "SELECT * FROM auth_users")
        (.then (fn [rows]
                 (js/Promise.all
                  (map (fn [row]
                         (let [id (:id row)]
                           (-> (js/Promise.all [(fetch-relations executor id "auth_user_groups" "user_id" "group_id")
                                                (fetch-relations executor id "auth_user_roles" "user_id" "role_id")])
                               (.then (fn [[groups roles]]
                                        (-> (fetch-permissions-for-roles executor roles)
                                            (.then (fn [perms]
                                                     (assoc row :id (str->kw id) :groups groups :roles roles :permissions perms)))))))))
                       rows)))))
    :groups
    (-> (executor "SELECT * FROM auth_groups")
        (.then (fn [rows]
                 (js/Promise.all
                  (map (fn [row]
                         (let [id (:id row)]
                           (-> (js/Promise.all [(fetch-relations executor id "auth_group_roles" "group_id" "role_id")
                                                (fetch-relations executor id "auth_group_permissions" "group_id" "perm_id")])
                               (.then (fn [[roles perms]]
                                        (assoc row :id (str->kw id) :roles roles :permissions perms))))))
                       rows)))))
    :roles
    (-> (executor "SELECT * FROM auth_roles")
        (.then (fn [rows]
                 (js/Promise.all
                  (map (fn [row]
                         (let [id (:id row)]
                           (-> (fetch-relations executor id "auth_role_permissions" "role_id" "perm_id")
                               (.then (fn [perms]
                                        (assoc row :id (str->kw id) :permissions perms))))))
                       rows)))))
    :permissions
    (-> (executor "SELECT * FROM auth_permissions")
        (.then (fn [rows] (map #(update % :id str->kw) rows))))))

(defn get-handler [executor {collection :collection id :id}]
  ;; Simplified: just reuse list and filter (not efficient but safe for demo)
  (-> (list-handler executor {:collection collection})
      (.then (fn [items]
               (or (some #(when (= (:id %) (str->kw id)) %) items)
                   (throw (ex-info "Not found" {:id id})))))))

(defn create-handler [executor {collection :collection data :data}]
  (let [id (kw->str (:id data))
        name-or-desc (or (:name data) (:description data))]
    (case (ensure-kw collection)
      :users
      (-> (executor "INSERT INTO auth_users VALUES (?, ?)" [id name-or-desc])
          (.then #(update-relations executor id "auth_user_groups" "user_id" "group_id" (:groups data)))
          (.then #(update-relations executor id "auth_user_roles" "user_id" "role_id" (:roles data)))
          (.then (fn [] data)))
      :groups
      (-> (executor "INSERT INTO auth_groups VALUES (?, ?)" [id name-or-desc])
          (.then #(update-relations executor id "auth_group_roles" "group_id" "role_id" (:roles data)))
          (.then #(update-relations executor id "auth_group_permissions" "group_id" "perm_id" (:permissions data)))
          (.then (fn [] data)))
      :roles
      (-> (executor "INSERT INTO auth_roles VALUES (?, ?)" [id name-or-desc])
          (.then #(update-relations executor id "auth_role_permissions" "role_id" "perm_id" (:permissions data)))
          (.then (fn [] data)))
      :permissions
      (-> (executor "INSERT INTO auth_permissions VALUES (?, ?)" [id name-or-desc])
          (.then (fn [] data))))))

(defn update-handler [executor {collection :collection id :id data :data}]
  (let [sid (kw->str id)
        name-or-desc (or (:name data) (:description data))]
    (case (ensure-kw collection)
      :users
      (-> (executor "UPDATE auth_users SET name = ? WHERE id = ?" [name-or-desc sid])
          (.then #(update-relations executor sid "auth_user_groups" "user_id" "group_id" (:groups data)))
          (.then #(update-relations executor sid "auth_user_roles" "user_id" "role_id" (:roles data)))
          (.then (fn [] data)))
      :groups
      (-> (executor "UPDATE auth_groups SET description = ? WHERE id = ?" [name-or-desc sid])
          (.then #(update-relations executor sid "auth_group_roles" "group_id" "role_id" (:roles data)))
          (.then #(update-relations executor sid "auth_group_permissions" "group_id" "perm_id" (:permissions data)))
          (.then (fn [] data)))
      :roles
      (-> (executor "UPDATE auth_roles SET description = ? WHERE id = ?" [name-or-desc sid])
          (.then #(update-relations executor sid "auth_role_permissions" "role_id" "perm_id" (:permissions data)))
          (.then (fn [] data)))
      :permissions
      (-> (executor "UPDATE auth_permissions SET description = ? WHERE id = ?" [name-or-desc sid])
          (.then (fn [] data))))))

(defn delete-handler [executor {collection :collection id :id}]
  (let [sid (kw->str id)
        table (case (ensure-kw collection) :users "auth_users" :groups "auth_groups" :roles "auth_roles" :permissions "auth_permissions")]
    ;; Note: Cascading deletes for relations are not implemented here for brevity, but should be.
    (executor (str "DELETE FROM " table " WHERE id = ?") [sid])))
