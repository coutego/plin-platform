(ns plinpt.i-js-utils
  "Interface plugin for JavaScript utilities.
   
   Provides helper functions for JS plugin developers to work with
   ClojureScript data structures, atoms, and React integration."
  (:require [plin.core :as plin]
            [plinpt.i-devdoc :as idev]))

(def plugin
  (plin/plugin
   {:doc "Interface for JavaScript utilities. Provides helpers for atoms, data access, and React hooks."
    :deps [idev/plugin]
    
    :contributions
    {::idev/plugins [{:id :i-js-utils
                      :description "Interface for JavaScript Utilities."
                      :responsibilities "Defines beans for JS interop helpers including atom utilities, data access, and React hooks."
                      :type :infrastructure}]}
    
    :beans
    {::api
     ^{:doc "JavaScript utilities API.
             
             Provides the following utilities:
             
             ## Atom Utilities
             - isAtom(value): Check if value is an atom wrapper
             - deref(maybeAtom): Safely dereference, works on atoms or plain values
             
             ## Data Access
             - get(obj, key): Smart key lookup (tries multiple key formats)
             - getIn(obj, path): Nested access with array path
             - keys(obj): Get all keys as array
             - vals(obj): Get all values as array
             
             ## Type Checking
             - isSet(value): Check if value is a JS Set (from CLJS set)
             - isArray(value): Check if value is an array
             
             ## Collection Utilities
             - toArray(coll): Convert any collection to JS array
             
             ## React Hooks
             - useAtom(atom): Subscribe to atom, re-render on change
             - useAtomState(atom): Returns [value, setValue] like useState
             
             ## Navigation (requires router)
             - navigate(path): Navigate to route
             - currentPath(): Get current route path"
       :api {:ret :object}}
     [:= nil]}}))
