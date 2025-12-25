(ns plinpt.p-debug.bean-graph
  (:require [clojure.string :as str]
            [plinpt.p-debug.common :as common]))

(defn- sanitize-id [k]
  (-> (str k)
      (str/replace #":" "")
      (str/replace #"/" "_")
      (str/replace #"\." "_")
      (str/replace #"-" "_")))

(defn- extract-deps [bean-def]
  (cond
    ;; Easy Syntax: [fn-var arg1 arg2]
    (vector? bean-def)
    (let [head (first bean-def)]
      (if (= head :=)
        [] ;; Literal value, no deps
        ;; Function call, rest are args/deps
        (filter keyword? (rest bean-def))))
    
    ;; Map Syntax (Legacy/Verbose): {:constructor [fn ...]}
    (map? bean-def)
    (let [ctor (:constructor bean-def)]
      (if (vector? ctor)
        (filter keyword? (rest ctor))
        []))
    
    :else []))

(defn- generate-mermaid [definitions]
  (let [all-keys (keys definitions)
        format-id (common/make-id-formatter all-keys)
        
        ;; Calculate edges
        edges (for [[bean-key bean-def] definitions
                    dep (extract-deps bean-def)]
                [bean-key dep])
        
        active-nodes (set (flatten edges))]
    
    (if (empty? edges)
      "graph LR\n  subgraph Empty\n    No_Dependencies_Found\n  end\n"
      (let [nodes-str (for [node active-nodes]
                        (let [id (sanitize-id node)
                              label (format-id node)]
                          (str "  " id "[\"" label "\"]\n")))
            
            edges-str (for [[source target] edges]
                        (let [s-id (sanitize-id source)
                              t-id (sanitize-id target)]
                          (str "  " s-id " --> " t-id "\n")))]
        
        (str "graph LR\n"
             (str/join "" nodes-str)
             (str/join "" edges-str))))))

(defn main-view [sys-state]
  (let [{:keys [container]} @sys-state
        definitions (get container :plin.core/definitions {})
        graph-def (generate-mermaid definitions)]
    [:div {:class "flex flex-col h-full"}
     [:div {:class "p-4 border-b border-gray-200 bg-white"}
      [:h3 {:class "font-bold text-lg"} "Bean Dependency Graph"]
      [:p {:class "text-sm text-gray-500"} "Visualizing dependencies between beans (inferred from definitions)."]]
     
     [:div {:class "flex-1 overflow-hidden bg-gray-50"}
      [common/mermaid-view graph-def]]]))
