(ns pluggable.container
  "The Pluggable plugin container"
  (:require [malli.core :as m]
            [malli.error :as me]
            [clojure.string :as str]))

(def plugin-schema
  [:map
   [:id keyword?]
   [:doc {:optional true} string?]
   [:deps {:optional true} [:sequential [:or keyword? map? string?]]]
   [:loader {:optional true} fn?]])

(def plugins-schema
  [:sequential plugin-schema])

(defn- crash [msg] (throw (ex-info msg {:cause msg})))
(defn- crash-if [condition msg] (when condition (crash msg)))

(defn normalize-dep
  "Normalizes a dependency to a keyword ID.
   Accepts: keyword, plugin map, or string.
   
   Examples:
   - :plinpt.i-application/plugin -> :plinpt.i-application/plugin
   - {:id :plinpt.i-application/plugin ...} -> :plinpt.i-application/plugin
   - \"plinpt.i-application\" -> :plinpt.i-application/plugin"
  [dep]
  (cond
    (keyword? dep) dep
    (map? dep) (:id dep)
    (string? dep) (keyword dep "plugin")
    :else (throw (ex-info (str "Invalid dependency format: " dep) {:dep dep}))))

(defn- make-plugin-list [plugin parents loaded plugins-by-id]
  (let [plugin-id (:id plugin)]
    (cond
      (contains? (set (map :id loaded)) plugin-id)
      loaded

      (contains? (set parents) plugin-id)
      (crash (str "Cyclic dependencies: "
                  plugin-id
                  " depends on itself through "
                  (str/join ", " parents)))

      :else
      (let [dep-ids (map normalize-dep (or (:deps plugin) []))
            trans-deps
            (reduce (fn [acc dep-id]
                      (if-let [dep-plugin (get plugins-by-id dep-id)]
                        (make-plugin-list dep-plugin (conj parents plugin-id) acc plugins-by-id)
                        ;; Dependency not found in current plugin set - skip
                        ;; This allows for optional dependencies or deps satisfied later
                        acc))
                    loaded
                    dep-ids)]
        (conj trans-deps plugin)))))

(defn process-plugin-deps
  "Ensures that dependencies are loaded in the right order, returning the list
   of plugins (which can be longer than the original one, because of declared
   dependencies). It throws an exception if cyclic dependencies are found.
   
   Dependencies can be specified as:
   - Keyword IDs: :plinpt.i-application/plugin
   - Plugin maps: {:id :plinpt.i-application/plugin ...}
   - Strings: \"plinpt.i-application\" (converted to :plinpt.i-application/plugin)"
  [plugins]
  ;; Build ID -> plugin lookup map
  (let [plugins-by-id (into {} (map (juxt :id identity) plugins))
        super-plugin {:id ::meta-plugin, :deps (map :id plugins)}
        ret          (butlast (make-plugin-list super-plugin [] [] plugins-by-id))
        ids          (map :id ret)
        dup          (->> ids frequencies (filter #(> (second %) 1)))]
    (crash-if (> (count dup) 0)
              (str "Duplicate plugin id: " dup))
    ret))

(defn load-plugins-impl [plugins db]
  (if (= (count plugins) 0)
    db
    (recur (rest plugins)
           (if-let [loader (-> plugins first :loader)]
             (loader db plugins)
             db))))

(defn load-plugins
  [plugins & [db]]
  (crash-if (not (vector? plugins))
            "pluggable.core/load-plugins: plugins need to be passed as a vector")
  (crash-if (not (or (nil? db) (map? db)))
            "pluggable.core/load-plugins: db must be a map")
  (when-let [errors (m/explain plugins-schema plugins)]
    (crash-if true
              (str "pluggable.core/load-plugins: plugins does not comply with schema: "
                   (me/humanize errors))))
  (load-plugins-impl (process-plugin-deps plugins) (or db {})))
