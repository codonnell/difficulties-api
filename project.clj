(defproject difficulty-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [medley "0.7.3"]
                 [metosin/compojure-api "1.0.0"]
                 [clj-http "2.1.0"]
                 [http-kit "2.1.18"]
                 [cheshire "5.5.0"]
                 [org.clojure/core.async "0.2.374"]
                 [com.datomic/datomic-pro "0.9.5350"]
                 [com.stuartsierra/component "0.3.1"]]
  :ring {:handler difficulty-api.handler/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[reloaded.repl "0.2.1"]
                                  [javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.0"]]
                   :plugins [[lein-ring "0.9.7"]]}})
