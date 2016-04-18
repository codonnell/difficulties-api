(defproject difficulty-api "0.1.0"
  :description "Computes difficulties of attacks in torn and serves those difficulties via an API."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [medley "0.7.3"]
                 [metosin/compojure-api "1.0.0"]
                 [clj-http "2.1.0"]
                 [http-kit "2.1.18"]
                 [cheshire "5.5.0"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/core.memoize
                                                                org.clojure/tools.reader]]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [com.datomic/datomic-pro "0.9.5350"]
                 [com.stuartsierra/component "0.3.1"]
                 [levand/immuconf "0.1.0"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.taoensso/timbre "4.3.1"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username [:gpg :env/datomic_username]
                                   :password [:gpg :env/datomic_password]}}
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[reloaded.repl "0.2.1"]]}
             :uberjar {:main difficulty-api.main
                       :aot [difficulty-api.main]}})
