(ns plin-platform.server-boot
  "Server-side bootstrap entry point.
   
   Loads plugins for server mode and calls boot/bootstrap!
   
   Note: In nbb, we cannot dynamically require namespaces based on manifest,
   so server plugins are explicitly required here."
  (:require [plin.boot :as boot]
            [clojure.string :as str]
            ["path" :as path]
            ["url" :as url]
            
            ;; Interfaces
            [plinpt.i-devdoc :as i-devdoc]
            [plinpt.i-router :as i-router]
            [plinpt.i-application :as i-app]
            [plinpt.i-service-invoker :as i-invoker]
            [plinpt.i-db :as i-db]
            [plinpt.i-tracer :as i-tracer]
            [plinpt.i-service-authorization :as i-service-auth]
            
            ;; Implementations
            [plinpt.p-alasql-db :as p-db]
            [plinpt.p-service-invoker-demo :as p-invoker]
            [plinpt.p-service-authorization :as p-service-auth]
            [plinpt.p-server-boot :as p-server-boot]))

;; --- Path Resolution ---

(def user-root (js/process.cwd))

;; In nbb/ESM, __filename is not available. Use import.meta.url instead.
;; However, in nbb we can just use process.cwd() as the framework root
;; since nbb is typically run from the project root.
(def framework-root
  (let [cwd (js/process.cwd)]
    ;; Check if we're running from node_modules (as a dependency)
    (if (str/includes? cwd "node_modules")
      (path/resolve cwd "../..")
      cwd)))

;; --- Plugin List ---
;; These are the plugins loaded for server mode.
;; They correspond to manifest entries with :server or :shared tags.

(def server-plugins
  [;; Interfaces (shared)
   i-devdoc/plugin
   i-router/plugin
   i-app/plugin
   i-invoker/plugin
   i-db/plugin
   i-tracer/plugin
   i-service-auth/plugin
   
   ;; Implementations (server)
   p-db/plugin
   p-invoker/plugin
   p-service-auth/plugin
   p-server-boot/plugin])

;; --- Main ---

(defn -main [& args]
  (println "PLIN Server Bootstrap")
  (println "Framework root:" framework-root)
  (println "User root:" user-root)
  (println "Loading" (count server-plugins) "plugins...")
  
  (boot/bootstrap! server-plugins))

(-main)
