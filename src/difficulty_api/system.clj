(ns difficulty-api.system
  (:require [com.stuartsierra.component :as component]
            [difficulty-api.db :refer [new-database]]
            [difficulty-api.torn-api :refer [clj-http-client]]
            [difficulty-api.server :refer [new-server]]
            [difficulty-api.handler :refer [new-app]]))

(defn dev-system [config-options]
  (let [{:keys [db-uri]} config-options]
    (component/system-map
     :db (new-database db-uri)
     :app (component/using
           (new-app {:http-client clj-http-client})
           [:db])
     :server (component/using
              (new-server {})
              [:app]))))

(defn test-system [config-options]
  (let [{:keys [db-uri http-client] :or ["datomic:mem://difficulty-api-test" clj-http-client] config-options}]
    (component/system-map
     :db (new-database db-uri)
     :app (component/using
           (new-app {:http-client http-client})
           [:db]))))
