(ns plinpt.p-schema-visualizer.core
  (:require [clojure.string :as str]))

(defn- get-current-db-name []
  "Gets the current database name from AlaSQL."
  (try
    (or (.-useid js/alasql) "alasql")
    (catch :default _ "alasql")))

(defn- get-table-metadata [db-name table-name]
  "Retrieves table metadata including constraints from AlaSQL's internal structure."
  (try
    (let [databases (.-databases js/alasql)
          db (when databases (aget databases db-name))
          tables (when db (.-tables db))
          table-obj (when tables (aget tables table-name))]
      (when table-obj
        {:columns (js->clj (.-columns table-obj) :keywordize-keys true)
         :pk (.-pk table-obj)
         ;; AlaSQL stores foreign keys in different places depending on version/creation method
         :foreignKeys (or (js->clj (.-foreignKeys table-obj) :keywordize-keys true) [])
         :constraints (or (js->clj (.-constraints table-obj) :keywordize-keys true) [])
         ;; Also check for fk property
         :fk (js->clj (.-fk table-obj) :keywordize-keys true)}))
    (catch :default e
      (js/console.warn "Failed to get metadata for table:" table-name e)
      nil)))

(defn- extract-foreign-keys-from-columns [columns]
  "Extracts foreign key information from column definitions."
  (when (map? columns)
    (for [[col-name col-def] columns
          :when (map? col-def)
          :let [refs (or (:references col-def) 
                         (:foreignkey col-def)
                         (when-let [fk (:fk col-def)]
                           {:table (:table fk) :column (:column fk)}))]
          :when refs]
      {:column (name col-name)
       :references refs})))

(defn get-schema []
  (try
    ;; Try to use the demo database if available
    (try (js/alasql "USE pluggable_demo_db") (catch :default _ nil))
    
    (let [db-name (get-current-db-name)
          _ (js/console.log "Schema Visualizer: Using database:" db-name)
          tables (js->clj (js/alasql "SHOW TABLES") :keywordize-keys true)]
      (mapv (fn [t]
              (let [tid (:tableid t)
                    ;; Get columns via SHOW COLUMNS (reliable)
                    cols (js->clj (js/alasql (str "SHOW COLUMNS FROM " tid)) :keywordize-keys true)
                    ;; Get internal metadata for constraints
                    metadata (get-table-metadata db-name tid)
                    ;; Extract FK info from column definitions
                    col-fks (extract-foreign-keys-from-columns (:columns metadata))]
                (do
                  (when (or (seq (:foreignKeys metadata)) 
                            (seq (:constraints metadata))
                            (seq (:fk metadata))
                            (seq col-fks))
                    (js/console.log "Table" tid "metadata:" (clj->js metadata))
                    (js/console.log "Table" tid "column FKs:" (clj->js col-fks)))
                  {:table tid
                   :columns (mapv :columnid cols)
                   :foreignKeys (:foreignKeys metadata)
                   :constraints (:constraints metadata)
                   :fk (:fk metadata)
                   :columnFks col-fks})))
            tables))
    (catch :default e
      (js/console.error "Schema extraction failed" e)
      [])))

(defn infer-relationships [schema]
  (let [tables (set (map :table schema))
        
        ;; 1. Explicit Foreign Keys from AlaSQL foreignKeys array
        fk-rels (for [t schema
                      fk (:foreignKeys t)
                      :when (and fk (:table fk))
                      :let [target-table (or (:table fk) (:tableid fk))]
                      :when target-table]
                  {:from (:table t)
                   :to target-table
                   :label (or (:column fk) (:columnid fk) "fk")
                   :source :foreignKeys})
        
        ;; 2. Explicit Foreign Keys from constraints array
        constraint-rels (for [t schema
                              c (:constraints t)
                              :when (and c 
                                         (or (= "FOREIGN KEY" (str/upper-case (or (:type c) "")))
                                             (= :foreignkey (:type c))
                                             (:references c)))
                              :let [ref (or (:references c) c)
                                    target-table (or (:table ref) (:tableid ref))]
                              :when target-table]
                          {:from (:table t)
                           :to target-table
                           :label (str/join ", " (or (:columns c) [(:column c) (:columnid c)]))
                           :source :constraints})
        
        ;; 3. Foreign Keys from fk property
        fk-prop-rels (for [t schema
                          :when (:fk t)
                          [col-name fk-def] (:fk t)
                          :when fk-def
                          :let [target-table (or (:table fk-def) (:tableid fk-def))]
                          :when target-table]
                       {:from (:table t)
                        :to target-table
                        :label (name col-name)
                        :source :fk-prop})
        
        ;; 4. Foreign Keys from column definitions
        col-fk-rels (for [t schema
                         fk (:columnFks t)
                         :when fk
                         :let [refs (:references fk)
                               target-table (or (:table refs) (:tableid refs))]
                         :when target-table]
                      {:from (:table t)
                       :to target-table
                       :label (:column fk)
                       :source :column-def})
        
        ;; Combine all explicit relationships
        explicit-rels (concat fk-rels constraint-rels fk-prop-rels col-fk-rels)
        explicit-pairs (set (map (juxt :from :to) explicit-rels))
        
        ;; 5. Heuristic (Fallback) - only if no explicit FK found
        heuristic-rels (for [t schema
                            col (:columns t)
                            :let [col-name (name col)]
                            :when (and (str/ends-with? col-name "_id")
                                       (not= col-name "id"))
                            :let [base (str/replace col-name #"_id$" "")
                                  ;; Try various table name patterns
                                  candidates [(str base "s")           ;; user_id -> users
                                              (str base "es")          ;; status_id -> statuses
                                              base                     ;; category_id -> category
                                              (str "auth_" base "s")   ;; role_id -> auth_roles
                                              (str "auth_" base "es")  ;; Special pluralization
                                              ;; Handle special cases
                                              (when (= base "perm") "auth_permissions")
                                              (when (= base "role") "auth_roles")
                                              (when (= base "group") "auth_groups")
                                              (when (= base "user") "auth_users")]
                                  target-table (first (filter #(and % (contains? tables %)) candidates))]
                            :when (and target-table
                                       ;; Avoid duplicates if explicit FK exists
                                       (not (contains? explicit-pairs [(:table t) target-table])))]
                        {:from (:table t)
                         :to target-table
                         :label col-name
                         :source :heuristic})]
    
    ;; Log what we found for debugging
    (js/console.log "Schema Visualizer - Explicit relationships:" (count explicit-rels))
    (js/console.log "Schema Visualizer - Heuristic relationships:" (count heuristic-rels))
    
    (concat explicit-rels heuristic-rels)))

(defn generate-mermaid [schema]
  (let [rels (infer-relationships schema)
        
        ;; Deduplicate relationships (same from/to pair)
        unique-rels (vals (group-by (juxt :from :to) rels))
        deduped-rels (map first unique-rels)
        
        tables-str (for [t schema]
                     (str "  " (:table t) " {\n"
                          (str/join "" (for [c (:columns t)]
                                         (str "    string " c "\n")))
                          "  }\n"))
        
        rels-str (for [r deduped-rels]
                   (str "  " (:from r) " }|..|| " (:to r) " : " (:label r) "\n"))]
    
    (str "erDiagram\n"
         (str/join "" tables-str)
         (str/join "" rels-str))))
