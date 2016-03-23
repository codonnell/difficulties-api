(ns user
  (:require [reloaded.repl :refer [system init start stop go reset reset-all]]
            [difficulty-api.system :refer [dev-system]]))

(reloaded.repl/set-init! #(dev-system {:db-uri "datomic:mem://difficulty-api"}))

