(ns user
  (:require [reloaded.repl :refer [system init start stop go reset reset-all]]
            [difficulty-api.system :refer [dev-system test-system]]
            [difficulty-api.db :as db]
            [difficulty-api.dispatch :as dispatch]
            [difficulty-api.torn-api :as api]
            [com.stuartsierra.component :as component]))

(reloaded.repl/set-init! #(dev-system {:db-uri "datomic:mem://difficulty-api"}))

