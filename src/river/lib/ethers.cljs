(ns river.lib.ethers
  (:require
   ["ethers" :as ethers]
   [re-frame.core     :refer [reg-fx reg-cofx dispatch]]))


;; Utils
;;

(defn- dispatch-f
  [f on-success on-error]
  (-> f
      (.then #(dispatch (conj on-success %)))
      (.catch #(dispatch (conj on-error %)))))


;; Metamask
;;

(defn request-accounts
  []
  (.request js/ethereum (js-obj "method" "eth_requestAccounts")))

;; Ethers
;;

(defn make-web3-provider
  [web3-provider]
  (new (.. ethers -providers -Web3Provider) web3-provider))

(defn list-accounts
  [provider]
  (.listAccounts provider))

(defn get-network
  [provider]
  (.getNetwork provider))

(defn make-contract
  [address abi signer]
  (new (.. ethers -Contract) address (clj->js abi) signer))

(defn valid-address?
  [address]
  (js-invoke (aget ethers "utils") "isAddress" address))


;; Coeffects
;;

(reg-cofx
 :with-provider
 (fn [cofx _]
   (when (exists? js/ethereum)
     (assoc cofx :provider (make-web3-provider js/ethereum)))))

(reg-cofx
 :with-signer
 (fn [{:keys [provider] :as cofx}]
   (->> (.getSigner provider)
        (assoc cofx :signer))))


;; Effects
;;

(reg-fx
 :metamask/connect
 (fn [{:keys [on-success on-failure]}]
   (dispatch-f (request-accounts) on-success on-failure)))

(reg-fx
 :ethers/list-accounts
 (fn [{:keys [provider on-success on-failure]}]
   (dispatch-f (list-accounts provider) on-success on-failure)))

(reg-fx
 :ethers/get-network
 (fn [{:keys [provider on-success on-failure]}]
   (dispatch-f (get-network provider) on-success on-failure)))

(reg-fx
 :ethers/init-contract
 (fn [{:keys [address abi signer on-success]}]
   (dispatch (conj on-success (make-contract address abi signer)))))
