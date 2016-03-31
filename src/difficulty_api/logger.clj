(ns difficulty-api.logger
  (:require [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :refer [rotor-appender]]
            [com.stuartsierra.component :as component]))

(def timbre-config
  {:appenders {:rotor (rotor-appender {:path "./log/difficulties-api.log"})}
   :output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})})

(defrecord Logger []
  component/Lifecycle

  (start [component]
    (timbre/merge-config! timbre-config)
    component)

  (stop [component]
    component))

(defn new-logger []
  (map->Logger {}))
