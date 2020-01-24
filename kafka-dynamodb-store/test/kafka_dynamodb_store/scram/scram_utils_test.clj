(ns kafka-dynamodb-store.scram.scram-utils-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer :all]
            [kafka-dynamodb-store.scram.scram-utils :as sut]))

;; SCRAM-SHA-512 is generated
(fact (:SCRAM-SHA-512 (sut/scram-with-all-mechanisms "Foo"))
      =>
      (contains {:stored-key string?
                 :salt       string?}))
