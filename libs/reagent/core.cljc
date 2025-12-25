(ns reagent.core)

;; Shim for Reagent on the server (nbb).
;; We use .cljc to ensure this takes precedence in resolution if possible.
;;
;; This file is NOT included in the browser build (tools/build_single_file.cljs),
;; so the browser uses the real Reagent library provided by Scittle.

(println "System: Loading Reagent Shim (Server-Side)...")

(def atom clojure.core/atom)

(defn cursor [_ _] (clojure.core/atom nil))
(defn next-tick [f] (js/setTimeout f 0))
(defn after-render [f] (js/setTimeout f 0))
(defn flush [] nil)
