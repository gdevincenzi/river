(ns river.lib.sablier
  (:require
   [re-frame.core     :refer [reg-fx reg-cofx dispatch]]
   [river.utils :as utils]))


;; Utils
;;

(defn- dispatch-on-tx-confirmed
  [f on-success on-failure]
  (-> f
      (.then #(.wait %))
      (.then #(dispatch (conj on-success %)))
      (.catch #(dispatch (conj on-failure %)))))


;; Sablier Protocol
;;

(defn create-stream
  "Params:
   contract:      instance of contract that implements Sablier protocol
   recipient:     address to which money will be streamed
   deposit:       amount of money to stream, in units of the streaming currency
   token-address: address of the ERC-20 token contract
   start-time:    unix timestamp for the stream to start, in seconds
   stop-time:     unix timestamp for the stream to stop, in secods  "
  [contract recipient deposit token-address start-time stop-time]
  (js-invoke contract "createStream" recipient deposit token-address start-time stop-time))

(defn get-stream
  [contract stream-id]
  (js-invoke contract "getStream" stream-id))


;; ERC20 Protocol
;;

(defn erc20-approve-spending
  [^ethers.Contract erc20-contract spender-address amount]
  (js-invoke erc20-contract "approve" spender-address amount))


;; Sablier subgraph queries
;;

(defn- query-streams-with-filter
  [filter]
  (str "{streams(where: " filter "){id sender recipient deposit startTime stopTime timestamp ratePerSecond}}"))

(defn streams-by-sender
  [address]
  (query-streams-with-filter (str "{sender: \""  address "\"}")))

(defn streams-by-recipient
  [address]
  (query-streams-with-filter (str "{recipient: \""  address "\"}")))


;; Stream calculations
;;

(defn stream-status
  [reference-time {:keys [start-time stop-time]}]
  (cond
    (> reference-time (js/parseInt stop-time))  :finished
    (> reference-time (js/parseInt start-time)) :ongoing
    :else                                       :queued))

(defn stream-progress
  [reference-time {:keys [start-time rate-per-second deposit]}]
  (-> reference-time
      (- (js/parseInt start-time))
      (* (js/parseInt rate-per-second))
      (/ (js/parseInt deposit))
      (* 100)
      (min 100)
      (.toFixed 2)))

(defn amount-transfered
  [reference-time stream]
  (min (js/BigInt (:deposit stream))
       (* (js/BigInt (:rate-per-second stream))
          (max (js/BigInt 0) (- (js/BigInt reference-time) (js/BigInt (:start-time stream)))))))


;; Coeffects
;;

(reg-cofx
 :with-stream-time-intervals
 (fn [cofx _]
   (let [[_ stream-data] (:event cofx)
         duration        (-> stream-data :duration (* 60 60)) ; duration expected in hours, convert to seconds
         start-time      (+ (utils/now-in-seconds) (* 5 60))        ; adds 5 minutes to start time
         stop-time       (+ start-time duration)
         delta-time      (- stop-time start-time)]
     (-> cofx
         (assoc :start-time start-time)
         (assoc :stop-time stop-time)
         (assoc :delta-time delta-time)))))

(reg-cofx
 :with-adjusted-deposit
 (fn [cofx _]
   (let [[_ stream-data] (:event cofx)
         amount          (js/BigInt (:amount stream-data))
         delta           (js/BigInt (:delta-time cofx))
         deposit         (- amount (js-mod amount delta))]
     (assoc cofx :deposit deposit))))


;; Effects
;;

(reg-fx
 :sablier/approve-spending
 (fn [{:keys [erc20-contract spender-address amount on-success on-failure]}]
   (dispatch-on-tx-confirmed
    (erc20-approve-spending erc20-contract spender-address amount)
    on-success
    on-failure)))

(reg-fx
 :sablier/create-stream
 (fn [{:keys [contract recipient deposit token-address start-time stop-time on-success on-failure]}]
   (dispatch-on-tx-confirmed
    (create-stream contract recipient deposit token-address start-time stop-time)
    on-success
    on-failure)))
