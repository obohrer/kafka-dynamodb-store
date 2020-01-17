(ns kafka-dynamodb-store.scram.scram-utils
  (:import [org.apache.kafka.common.security.scram.internals
            ScramFormatter
            ScramCredentialUtils
            ScramMechanism]
           [org.apache.kafka.common.security.scram
            ScramCredential]
           [java.util Base64])
  (:require [clojure.walk :as w]))


(def ^:const DEFAULT-SCRAM-ITERATIONS 8192)


(defn ^bytes b64->bytes
  [^String b64]
  (.decode (Base64/getDecoder) b64))


(defn ^String bytes->b64
  [^bytes bs]
  (.encodeToString (Base64/getEncoder) bs))


(defn m->ScramCredential
  "load b64 scram credential into a ScramCredential"
  [{:keys [salt stored-key server-key iterations]}]
  (ScramCredential. (b64->bytes salt)
                    (b64->bytes stored-key)
                    (b64->bytes server-key)
                    iterations))


(defn ScramCredential->m
  [^ScramCredential scram-cred]
  {:salt       (bytes->b64 (.salt scram-cred))
   :stored-key (bytes->b64 (.storedKey scram-cred))
   :server-key (bytes->b64 (.serverKey scram-cred))
   :iterations (.iterations scram-cred)})


(defn- scram-credential
  [iterations password mechanism]
  (let [credential (.generateCredential (ScramFormatter. mechanism)
                                        password
                                        iterations)]
    
    (ScramCredential->m credential)))


(defn- scram-mechanism-name
  [^ScramMechanism mechanism]
  (.mechanismName mechanism))


(defn scram-with-all-mechanisms
  [password]
  (->> (ScramMechanism/values)
     (map (juxt (comp keyword scram-mechanism-name) (partial scram-credential DEFAULT-SCRAM-ITERATIONS password)))
     (into {})))
