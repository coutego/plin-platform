(ns plinpt.p-js-utils.core
  "Core implementation of JavaScript utilities for JS plugin developers."
  (:require [plinpt.i-router :as irouter]))

;; =============================================================================
;; Atom Utilities
;; =============================================================================

(defn is-atom
  "Check if a value is an atom wrapper."
  [v]
  (and (some? v)
       (object? v)
       (true? (aget v "___isAtom"))))

(defn safe-deref
  "Safely dereference a value. If it's an atom wrapper, calls deref().
   Otherwise returns the value as-is."
  [v]
  (if (is-atom v)
    (let [deref-fn (aget v "deref")]
      (if (fn? deref-fn)
        (deref-fn)
        (aget v "value")))
    v))

;; =============================================================================
;; Data Access Utilities
;; =============================================================================

(defn smart-get
  "Smart key lookup that tries multiple key formats.
   Tries: key as-is, with leading colon, namespaced versions."
  [obj key]
  (when (and (some? obj) (object? obj))
    (let [key-str (if (keyword? key) (name key) (str key))]
      (or
       ;; Try exact key
       (aget obj key-str)
       ;; Try with colon prefix (for keyword strings like \":user/id\")
       (aget obj (str ":" key-str))
       ;; Try without namespace if key has one
       (when-let [slash-idx (and (string? key-str) (.indexOf key-str "/"))]
         (when (pos? slash-idx)
           (aget obj (subs key-str (inc slash-idx)))))))))

(defn get-in
  "Nested access with array path."
  [obj path]
  (reduce
   (fn [current key]
     (if (nil? current)
       nil
       (smart-get current key)))
   obj
   path))

(defn obj-keys
  "Get all keys of an object as an array."
  [obj]
  (if (and (some? obj) (object? obj))
    (js/Object.keys obj)
    (array)))

(defn obj-vals
  "Get all values of an object as an array."
  [obj]
  (if (and (some? obj) (object? obj))
    (js/Object.values obj)
    (array)))

;; =============================================================================
;; Type Checking
;; =============================================================================

(defn is-set
  "Check if value is a JS Set (typically from a CLJS set)."
  [v]
  (instance? js/Set v))

(defn is-array
  "Check if value is a JS array."
  [v]
  (array? v))

;; =============================================================================
;; Collection Utilities
;; =============================================================================

(defn to-array
  "Convert any collection to a JS array."
  [coll]
  (cond
    (nil? coll) (array)
    (array? coll) coll
    (instance? js/Set coll) (js/Array.from coll)
    (object? coll) (js/Object.values coll)
    :else (array coll)))

;; =============================================================================
;; React Hooks
;; =============================================================================

(defn use-atom
  "React hook that subscribes to an atom and re-renders on change.
   
   Usage in JS:
   const value = plin.useAtom(myAtom);
   
   Returns the current value of the atom."
  [atom-wrapper]
  (let [React (aget js/window "React")]
    (if-not React
      (do
        (js/console.warn "plin.useAtom: React not found on window")
        (safe-deref atom-wrapper))
      (let [useState (aget React "useState")
            useEffect (aget React "useEffect")
            useRef (aget React "useRef")
            ;; Get initial value
            initial-value (safe-deref atom-wrapper)
            ;; Create state
            state-arr (useState initial-value)
            value (aget state-arr 0)
            setValue (aget state-arr 1)
            ;; Track if this is an atom
            atom-ref (useRef atom-wrapper)]
        
        ;; Update ref if atom changes
        (aset atom-ref "current" atom-wrapper)
        
        ;; Subscribe to changes
        (useEffect
         (fn []
           (if-not (is-atom atom-wrapper)
             ;; Not an atom, no subscription needed
             js/undefined
             ;; Subscribe to atom changes
             (let [watch-fn (aget atom-wrapper "watch")]
               (if-not (fn? watch-fn)
                 js/undefined
                 (let [unwatch (watch-fn (fn [new-val]
                                           (setValue new-val)))]
                   ;; Return cleanup function
                   (fn []
                     (when (fn? unwatch)
                       (unwatch))))))))
         ;; Dependencies - re-subscribe if atom changes
         #js [atom-wrapper])
        
        ;; Return current value
        value))))

(defn use-atom-state
  "React hook that returns [value, setValue] for an atom, similar to useState.
   
   Usage in JS:
   const [value, setValue] = plin.useAtomState(myAtom);
   setValue(newValue);           // reset
   setValue(v => ({...v, x: 1})); // swap with function
   
   Returns [currentValue, setterFunction]."
  [atom-wrapper]
  (let [React (aget js/window "React")]
    (if-not React
      (do
        (js/console.warn "plin.useAtomState: React not found on window")
        #js [(safe-deref atom-wrapper) (fn [_] nil)])
      (let [useCallback (aget React "useCallback")
            ;; Get current value using useAtom
            value (use-atom atom-wrapper)
            ;; Create setter function
            setter (useCallback
                    (fn [new-val-or-fn]
                      (when (is-atom atom-wrapper)
                        (if (fn? new-val-or-fn)
                          ;; Function - use swap
                          (let [swap-fn (aget atom-wrapper "swap")]
                            (when (fn? swap-fn)
                              (swap-fn new-val-or-fn)))
                          ;; Value - use reset
                          (let [reset-fn (aget atom-wrapper "reset")]
                            (when (fn? reset-fn)
                              (reset-fn new-val-or-fn))))))
                    #js [atom-wrapper])]
        #js [value setter]))))

;; =============================================================================
;; Navigation Utilities
;; =============================================================================

(defn make-navigate-fn
  "Creates a navigate function using the injected router navigate! function."
  [navigate!]
  (fn [path]
    (when navigate!
      (navigate! path))))

(defn make-current-path-fn
  "Creates a currentPath function using the injected current-route atom."
  [current-route]
  (fn []
    (when current-route
      (let [route @current-route]
        (when route
          (:path route))))))

;; =============================================================================
;; API Factory
;; =============================================================================

(defn create-api
  "Creates the plin utilities API object.
   
   Arguments:
   - navigate!: Router navigate function
   - current-route: Router current-route atom"
  [navigate! current-route]
  (let [api (js-obj)]
    ;; Atom utilities
    (aset api "isAtom" is-atom)
    (aset api "deref" safe-deref)
    
    ;; Data access
    (aset api "get" smart-get)
    (aset api "getIn" get-in)
    (aset api "keys" obj-keys)
    (aset api "vals" obj-vals)
    
    ;; Type checking
    (aset api "isSet" is-set)
    (aset api "isArray" is-array)
    
    ;; Collection utilities
    (aset api "toArray" to-array)
    
    ;; React hooks
    (aset api "useAtom" use-atom)
    (aset api "useAtomState" use-atom-state)
    
    ;; Navigation
    (aset api "navigate" (make-navigate-fn navigate!))
    (aset api "currentPath" (make-current-path-fn current-route))
    
    api))
