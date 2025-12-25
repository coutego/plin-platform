(ns plinpt.p-schema-visualizer.core
  (:require [clojure.string :as str]))

(defn get-schema []
  (try
    (let [tables (js->clj (js/alasql "SHOW TABLES") :keywordize-keys true)]
      (mapv (fn [t]
              (let [tid (:tableid t)
                    cols (js->clj (js/alasql (str "SHOW COLUMNS FROM " tid)) :keywordize-keys true)
                    ;; Extract constraints from AlaSQL internal metadata
                    table-obj (aget js/alasql "tables" tid)
                    constraints (when table-obj 
                                  (js->clj (.-constraints table-obj) :keywordize-keys true))]
                {:table tid
                 :columns (mapv :columnid cols)
                 :constraints constraints}))
            tables))
    (catch :default e
      (js/console.error "Schema extraction failed" e)
      [])))

(defn infer-relationships [schema]
  (let [tables (set (map :table schema))
        
        ;; 1. Explicit Foreign Keys from AlaSQL
        explicit-rels (for [t schema
                            c (:constraints t)
                            :when (= "FOREIGN KEY" (str/upper-case (or (:type c) "")))
                            :let [ref (:references c)
                                  target-table (:table ref)]
                            :when target-table]
                        {:from (:table t)
                         :to target-table
                         :label (str/join ", " (:columns c))})
        
        ;; 2. Heuristic (Fallback)
        heuristic-rels (for [t schema
                             col (:columns t)
                             :let [col-name (name col)]
                             :when (and (str/ends-with? col-name "_id")
                                        (not= col-name "id"))
                             :let [base (str/replace col-name #"_id$" "")]
                             :let [target-table (cond
                                                  (contains? tables (str "auth_" base "s")) (str "auth_" base "s")
                                                  (contains? tables (str "auth_" base "es")) (str "auth_" base "es")
                                                  ;; Special case for perm_id -> auth_permissions
                                                  (and (= base "perm") (contains? tables "auth_permissions")) "auth_permissions"
                                                  :else nil)]
                             :when (and target-table
                                        ;; Avoid duplicates if explicit FK exists
                                        (not-any? #(and (= (:from %) (:table t)) 
                                                        (= (:to %) target-table)) 
                                                  explicit-rels))]
                         {:from (:table t)
                          :to target-table
                          :label base})]
    (concat explicit-rels heuristic-rels)))

(defn generate-mermaid [schema]
  (let [rels (infer-relationships schema)
        
        tables-str (for [t schema]
                     (str "  " (:table t) " {\n"
                          (str/join "" (for [c (:columns t)]
                                         (str "    string " c "\n")))
                          "  }\n"))
        
        rels-str (for [r rels]
                   (str "  " (:from r) " }|..|| " (:to r) " : " (:label r) "\n"))]
    
    (str "erDiagram\n"
         (str/join "" tables-str)
         (str/join "" rels-str))))
