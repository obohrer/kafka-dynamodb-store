(ns kafka-dynamodb-store.kms-utils
  (:require [amazonica.aws.kms :as kms]
            [amazonica.core :as aws]
            [safely.core :refer [safely]]
            [clojure.edn :as edn])
  (:import com.amazonaws.encryptionsdk.AwsCrypto
           com.amazonaws.encryptionsdk.kms.KmsMasterKeyProvider
           com.amazonaws.PredefinedClientConfigurations
           [com.amazonaws.regions Region Regions DefaultAwsRegionProviderChain]
           com.amazonaws.auth.AWSCredentialsProvider
           java.util.Collections
           java.util.Map))

(defonce ^:private ^AwsCrypto crypto
  (AwsCrypto.))


(defn- ^AWSCredentialsProvider amazonica-creds
  []
  (aws/get-credentials (some-> #'aws/credential deref deref)))


(defn- ^Region resolve-region
  [^String region-str]
  (Region/getRegion
   (Regions/fromName region-str)))


(defn encrypt
  [^String region ^String key-id ^String payload]
  (let [^KmsMasterKeyProvider master-key-provider
        (KmsMasterKeyProvider.
         (amazonica-creds)
         ^Region (resolve-region region)
         (PredefinedClientConfigurations/defaultConfig)
         key-id)
        out (.encryptString crypto master-key-provider payload ^Map {})]
    {:result      (.getResult out)
     :context     (into {} (.getEncryptionContext out))
     :algorithm   (str (.getCryptoAlgorithm out))
     :master-keys (into [] (.getMasterKeyIds out))}))

(defn decrypt
  [^String region ^String payload]
  (let [^KmsMasterKeyProvider master-key-provider
        (KmsMasterKeyProvider.
         (amazonica-creds)
         ^Region (resolve-region region)
         (PredefinedClientConfigurations/defaultConfig)
         (Collections/emptyList))
        out (.decryptString crypto master-key-provider payload)]

    {:result      (.getResult out)
     :context     (into {} (.getEncryptionContext out))
     :algorithm   (str (.getCryptoAlgorithm out))
     :master-keys (into [] (.getMasterKeyIds out))}))

