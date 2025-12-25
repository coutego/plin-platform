(ns reagent.core)

;; Shim for Reagent on the server (nbb).
;; This allows code that requires 'reagent.core' (like p-session/utils)
;; to run on the server without the 'react' npm package.
;;
;; This file is NOT included in the browser build (tools/build_single_file.cljs),
;; so the browser uses the real Reagent library provided by Scittle.

(def atom cljs.core/atom)

;; Minimal stubs for other common Reagent functions to prevent crashes
;; if they are referenced but not strictly used on the server.
(defn cursor [_ _] (cljs.core/atom nil))
(defn next-tick [f] (js/setTimeout f 0))
(defn after-render [f] (js/setTimeout f 0))
(defn flush [] nil)
