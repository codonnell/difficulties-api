(ns difficulty-api.repl
  (:require [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]))


(defrecord Repl [port]
  component/Lifecycle

  (start [component]
    (log/info "Starting repl...")
    (assoc component :server (start-server :port port)))

  (stop [component]
    (when-let [server (:server component)]
      (log/info "Stopping repl.")
      (stop-server server))
    (assoc component :server nil)))

(defn new-repl [port]
  (map->Repl {:port port}))
