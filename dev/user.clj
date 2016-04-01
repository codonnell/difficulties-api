(ns user
  (:require [reloaded.repl :refer [system init start stop go reset reset-all]]
            [difficulty-api.system :as system]
            [difficulty-api.db :as db]
            [difficulty-api.dispatch :as dispatch]
            [difficulty-api.torn-api :as api]
            [com.stuartsierra.component :as component]))

(reloaded.repl/set-init! #(system/system {}))

