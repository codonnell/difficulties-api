(ns difficulty-api.main
  (:require [difficulty-api.system :as system]
            [com.stuartsierra.component :as component])
  (:gen-class))

(defn -main [& args]
  (let [sys (system/system {})]
    (try
      (component/start-system sys)
      (finally (component/stop-system sys)))))
