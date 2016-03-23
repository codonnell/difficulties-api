(ns difficulty-api.server
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]
            [difficulty-api.handler :refer [app]]
            [compojure.api.middleware :refer [wrap-components]]))

(defrecord HttpKit [app]
  component/Lifecycle

  (start [component]
    (assoc component :http-kit (httpkit/run-server (:app app) {:port 3000})))

  (stop [component]
    (if-let [stop-fn (:http-kit component)]
      (stop-fn))
    (assoc component :http-kit nil)))

(defn new-server [{:keys [app] :as config}]
  (map->HttpKit config))
