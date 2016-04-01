(ns difficulty-api.system
  (:require [com.stuartsierra.component :as component]
            [immuconf.config :as config]
            [difficulty-api.logger :refer [new-logger]]
            [difficulty-api.db :refer [new-database]]
            [difficulty-api.torn-api :refer [clj-http-client]]
            [difficulty-api.server :refer [new-server]]
            [difficulty-api.handler :refer [new-app]]))

(defn prod-system [config-options]
  (let [env-file (config/load "resources/env.edn" :config-file)
        conf (merge config-options (config/load "resources/config.edn" env-file))]
    (component/system-map
     :logger (new-logger)
     :db (new-database (get-in conf [:database :uri]))
     :app (component/using
           (new-app {:http-client clj-http-client})
           [:db])
     :server (component/using
              (new-server {:port (get-in conf [:server :port])})
              [:app]))))

(defn dev-system [config-options]
  (let [env-file (config/load "resources/env.edn" :config-file)
        conf (merge config-options (config/load "resources/config.edn"))]
    (component/system-map
     :logger (new-logger)
     :db (new-database (get-in conf [:database :uri]))
     :app (component/using
           (new-app {:http-client clj-http-client})
           [:db])
     :server (component/using
              (new-server {:port (get-in conf [:server :port])})
              [:app]))))

(defn test-system [config-options]
  (let [env-file (config/load "resources/env.edn" :config-file)
        {:keys [http-client] :or {http-client clj-http-client} :as conf}
        (merge config-options (config/load "resources/config.edn" env-file))]
    (component/system-map
     :logger (new-logger)
     :db (new-database (get-in conf [:database :uri]))
     :app (component/using
           (new-app {:http-client http-client})
           [:db]))))
