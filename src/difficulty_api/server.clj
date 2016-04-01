(ns difficulty-api.server
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [org.httpkit.server :as httpkit]
            [difficulty-api.handler :refer [app]]
            [compojure.api.middleware :refer [wrap-components]]))

(defrecord HttpKit [app port]
  component/Lifecycle

  (start [component]
    (log/info "Starting server...")
    (assoc component :http-kit (httpkit/run-server (:app app) {:port port})))

  (stop [component]
    (log/info "Shutting down server...")
    (if-let [stop-fn (:http-kit component)]
      (stop-fn))
    (assoc component :http-kit nil)))

(defn new-server [{:keys [app port] :as config}]
  (map->HttpKit config))
