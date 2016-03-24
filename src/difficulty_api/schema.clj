(ns difficulty-api.schema
  (:require [schema.core :as s]))

(def Attack
  {:attack/torn-id s/Int
   :attack/defender s/Int
   :attack/attacker (s/maybe s/Int)
   :attack/result (s/enum :attack.result/stalemate
                          :attack.result/hospitalize
                          :attack.result/leave
                          :attack.result/mug
                          :attack.result/lose
                          :attack.result/run-away
                          :attack.result/timeout)
   :attack/timestamp-started s/Inst
   :attack/timestamp-ended s/Inst})

