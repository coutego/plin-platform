(ns plin-platform.server
  "Server entry point. Simply loads the server bootstrap."
  (:require [plin-platform.server-boot]))

;; The server-boot module handles everything:
;; - Loading plugins
;; - Calling boot/bootstrap!
;; - The p-server-boot plugin provides ::boot/boot-fn which starts the HTTP server

(defn -main [& args]
  ;; server-boot already calls -main on load
  nil)
