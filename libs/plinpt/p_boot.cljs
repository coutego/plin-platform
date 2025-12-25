(ns plinpt.p-boot
  (:require [plin.core :as plin]
            [plinpt.p-boot.core :as core]))

(def plugin
  (plin/plugin
   {:doc "System Bootstrapper. Manages the plugin lifecycle and system state."
    :deps []
    
    :beans
    {::api
     ^{:doc "System Control API (State and Reload)."
       :api {:ret :map}}
     [:= {:state core/state
          :reload! core/reload!}]}}))
