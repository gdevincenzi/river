(ns river.subs
  (:require
   [re-frame.core :refer [reg-sub]]
   [river.lib.sablier :as sablier]
   [river.utils       :as utils]))


;; Application
;;

(reg-sub
 :active-page
 (fn [db _]
   (:active-page db)))

(reg-sub
 :time
 (fn [db _]
   (:time db)))

(reg-sub
 :active-stream
 (fn [db _]
   (:active-stream db)))

(reg-sub
 :loading
 (fn [db _]
   (:loading db)))

(reg-sub
 :notifications
 (fn [db _]
   (:notifications db)))

(reg-sub
 :new-stream-modal
 (fn [db _]
   (get-in db [:modal :new-stream])))

(reg-sub
 :burger-menu
 (fn [db _]
   (:burger-menu db)))


;; Ethereum / Web3
;;

(reg-sub
 :selected-address
 (fn [db _]
   (get-in db [:user :address])))

(reg-sub
 :chain-id
 (fn [db _]
   (get-in db [:ethers :chain-id])))


;; Sablier / Streams
;;

(reg-sub
 :inbound-streams
 (fn [db _]
   (let [streams (get-in db [:user :inbound-streams])]
     (->> streams
          (map #(assoc % :starts-on (utils/seconds->locale-string (:start-time %))))
          (map #(assoc % :ends-on (utils/seconds->locale-string (:stop-time %))))
          (map #(assoc % :progress (sablier/stream-progress (utils/now-in-seconds) %)))
          (map #(assoc % :status (sablier/stream-status (utils/now-in-seconds) %)))))))

(reg-sub
 :recent-in-streams
 :<- [:inbound-streams]
 (fn [inbound-streams]
   (take 5 inbound-streams)))

(reg-sub
 :outbound-streams
 (fn [db _]
  (let [streams (get-in db [:user :outbound-streams])]
     (->> streams
          (map #(assoc % :starts-on (utils/seconds->locale-string (:start-time %))))
          (map #(assoc % :ends-on (utils/seconds->locale-string (:stop-time %))))
          (map #(assoc % :progress (sablier/stream-progress (utils/now-in-seconds) %)))
          (map #(assoc % :status (sablier/stream-status (utils/now-in-seconds) %)))))))

(reg-sub
 :recent-out-streams
 :<- [:outbound-streams]
 (fn [outbound-streams]
   (take 5 outbound-streams)))

(reg-sub
 :stream-detail
 :<- [:inbound-streams]
 :<- [:outbound-streams]
 :<- [:active-stream]
 :<- [:time]
 (fn [[inbound-streams outbound-streams active-stream time]]
   (let [stream (->> (concat inbound-streams outbound-streams)
                     (filter #(= active-stream (:id %)))
                     (first))]
     (when stream
       (-> stream
           (assoc :amount-transfered (.toString (sablier/amount-transfered time stream)))
           (assoc :progress (sablier/stream-progress time stream)))))))
