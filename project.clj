(defproject com.obohrer/kafka-dynamodb-store "0.0.1-SNAPSHOT"
  :description "Kafka storage handlers to bypass zookeeper"
  :url "https://github.com/obohrer/kafka-dynamodb-store"

  :license {:name "APACHE LICENSE, VERSION 2.0"
            :url "https://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.logging "0.5.0"]

                 [com.brunobonacci/safely "0.5.0-alpha8"]

                 [org.apache.kafka/kafka-clients "2.4.0"]
                 ;; aws
                 [amazonica "0.3.152"
                  :exclusions [com.amazonaws/aws-java-sdk
                               com.amazonaws/aws-java-sdk-core]]
                 [com.amazonaws/aws-java-sdk-core "1.11.705"]
                 [com.amazonaws/aws-java-sdk-sts  "1.11.705"]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.11.705"]
                 ]

  :repl-options {:init-ns kafka-dynamodb-store.scram.core}
  :profiles {:dev
             {:dependencies [[org.apache.kafka/kafka_2.12 "2.4.0"]
                             [midje/midje "1.9.8"]]
              :plugins [[lein-midje "3.2"]]}}
  :aot [kafka-dynamodb-store.scram.callback-handler])
