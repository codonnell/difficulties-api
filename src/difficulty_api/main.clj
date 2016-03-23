(ns difficulty-api.main
  (:require [difficulty-api.system :as system]
            [com.stuartsierra.component :as component]))

(defn -main [& args]
  (component/start (system/dev-system {:db-uri "datomic:mem://difficulty-api"})))
