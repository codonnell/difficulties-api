(ns difficulty-api.system
  (:require [com.stuartsierra.component :as component]
            [difficulty-api.db :refer [new-database]]
            [difficulty-api.handler :refer [app]]))

(defn dev-system [config-options]
  (let [{:keys [db-uri]} config-options]
    (component/system-map
     :db (new-database db-uri)
     :app (component/using
           (app)
           [:db]))))
