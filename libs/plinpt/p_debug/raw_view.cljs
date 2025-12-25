(ns plinpt.p-debug.raw-view
  (:require [clojure.pprint :as pprint]))

(defn- sanitize [x]
  (cond
    ;; atom? might not be available in all sci contexts, check for IAtom or similar if needed
    ;; but usually checking if it satisfies IDeref and IAtom is safer if atom? is missing.
    ;; For now, let's try a safer check or just assume if it has -reset! it is an atom-like thing
    ;; or just check type if possible.
    ;; Actually, let's just check if it is an object and has -reset! which atoms do.
    (and (satisfies? IDeref x) (satisfies? IReset x)) "<Atom>"
    (fn? x) "<Function>"
    (map? x) (into {} (map (fn [[k v]] [k (sanitize v)]) x))
    (vector? x) (mapv sanitize x)
    (set? x) (set (map sanitize x))
    (seq? x) (doall (map sanitize x))
    :else x))

(defn- format-segmented [items header-fn content-fn]
  (apply str
         (for [item items]
           (let [content (content-fn item)]
             (when (or (seq content) (map? content)) ;; Only show if there is content
               (str ";; =============================================================================\n"
                    ";; " (header-fn item) "\n"
                    ";; =============================================================================\n"
                    (with-out-str (pprint/pprint (sanitize content)))
                    "\n"))))))

(defn main-view [data title subtitle & [options]]
  (let [sanitized-data (if (:segmented? options)
                         data ;; format-segmented handles sanitization per item
                         (sanitize data))
        text (if (:segmented? options)
               (format-segmented sanitized-data (:header-fn options) (:content-fn options))
               (with-out-str (pprint/pprint sanitized-data)))]
    [:div {:class "flex flex-col h-full"}
     [:div {:class "p-4 border-b border-gray-200 bg-white"}
      [:h3 {:class "font-bold text-lg"} title]
      [:p {:class "text-sm text-gray-500"} subtitle]]
     
     [:div {:class "flex-1 overflow-auto bg-gray-900 p-4"}
      [:pre {:class "text-xs font-mono text-green-400 whitespace-pre"}
       text]]]))
