(defproject difficulty-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [medley "0.7.3"]
                 [metosin/compojure-api "1.0.0"]
                 [com.datomic/datomic-pro "0.9.5350"]
                 [com.stuartsierra/component "0.3.1"]]
  :ring {:handler difficulty-api.handler/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [cheshire "5.5.0"]
                                  [ring/ring-mock "0.3.0"]]
                   :plugins [[lein-ring "0.9.7"]]}})
