(ns plinpt.p-session.service
  (:require [clojure.string :as str]))

(defn- sql-val [v]
  (cond
    (string? v) (str "'" v "'")
    (keyword? v) (str "'" (name v) "'")
    (set? v) (str "'" (js/JSON.stringify (clj->js v)) "'") ;; Store sets as JSON strings
    (nil? v) "NULL"
    :else v))

(defn list-handler [db {collection :collection}]
  (let [table (name collection)]
    (db (str "SELECT * FROM " table) nil)))

(defn get-handler [db {collection :collection id :id}]
  (let [table (name collection)]
    (-> (db (str "SELECT * FROM " table " WHERE id = ?") [id])
        (.then (fn [res]
                 (if (not-empty res)
                   (first res)
                   (throw (ex-info (str "Entity not found: " id) {}))))))))

(defn create-handler [db {collection :collection data :data}]
  (let [table (name collection)
        ;; We assume data keys match table columns.
        ;; We need to convert Clojure data (like sets) to something SQL-friendly (like JSON strings)
        ;; if we are using a generic handler.
        clean-data (into {} (map (fn [[k v]] [k (if (set? v) (js/JSON.stringify (clj->js v)) v)]) data))
        keys (keys clean-data)
        cols (str/join ", " (map name keys))
        placeholders (str/join ", " (repeat (count keys) "?"))
        vals (vals clean-data)]
    (-> (db (str "INSERT INTO " table " (" cols ") VALUES (" placeholders ")") vals)
        (.then (fn [_] data)))))

(defn update-handler [db {collection :collection id :id data :data}]
  (let [table (name collection)
        clean-data (dissoc data :id)
        ;; Convert sets to JSON
        clean-data (into {} (map (fn [[k v]] [k (if (set? v) (js/JSON.stringify (clj->js v)) v)]) clean-data))
        keys (keys clean-data)
        assignments (str/join ", " (map #(str (name %) " = ?") keys))
        vals (concat (vals clean-data) [id])]
    (-> (db (str "UPDATE " table " SET " assignments " WHERE id = ?") vals)
        (.then (fn [_] (assoc data :id id))))))

(defn delete-handler [db {collection :collection id :id}]
  (let [table (name collection)]
    (-> (db (str "DELETE FROM " table " WHERE id = ?") [id])
        (.then (fn [_] true)))))
