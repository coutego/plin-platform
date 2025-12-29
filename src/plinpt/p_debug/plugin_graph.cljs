(ns plinpt.p-debug.plugin-graph
  (:require [clojure.string :as str]
            [plinpt.p-debug.common :as common]))

(defn- get-dep-id [dep]
  (if (keyword? dep) dep (:id dep)))

(defn- sanitize-id [k]
  (-> (str k)
      (str/replace #":" "")
      (str/replace #"/" "_")
      (str/replace #"\." "_")
      (str/replace #"-" "_")))

(defn- generate-mermaid [plugins disabled-ids]
  (let [format-id (common/make-id-formatter (map :id plugins))
        nodes (for [p plugins]
                (let [id (sanitize-id (:id p))
                      label (format-id (:id p))
                      disabled? (contains? disabled-ids (:id p))
                      style (if disabled? "fill:#f3f4f6,stroke:#9ca3af,stroke-dasharray: 5 5" "fill:#dbeafe,stroke:#2563eb")]
                  (str "  " id "[\"" label "\"]\n"
                       "  style " id " " style "\n")))
        
        edges (for [p plugins
                    dep (:deps p)]
                (let [source (sanitize-id (:id p))
                      target (sanitize-id (get-dep-id dep))]
                  (str "  " source " --> " target "\n")))]
    
    (str "graph TD\n"
         (str/join "" nodes)
         (str/join "" edges))))

(defn main-view [sys-state]
  (let [{:keys [all-plugins disabled-ids]} @sys-state
        graph-def (generate-mermaid all-plugins disabled-ids)]
    [:div {:class "flex flex-col h-full"}
     [:div {:class "p-4 border-b border-gray-200 bg-white"}
      [:h3 {:class "font-bold text-lg"} "Plugin Dependency Graph"]
      [:p {:class "text-sm text-gray-500"} "Visualizing relationships between loaded plugins."]]
     
     [:div {:class "flex-1 overflow-hidden bg-gray-50"}
      [common/mermaid-view graph-def]]]))
