(ns difficulty-api.main
  (:require [difficulty-api.system :as system]
            [com.stuartsierra.component :as component]))

(defn -main [& args]
  (let [sys (system/dev-system {:db-uri "datomic:mem://difficulty-api"})]
    (try
      (component/start sys)
      (finally (component/stop sys)))))
