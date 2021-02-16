(ns river.events
  (:require
   [ajax.core     :as ajax]
   [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx dispatch]]
   [river.db      :as db]
   [river.lib.sablier :as sablier]
   [river.utils   :as utils]))


;; Initialization
;;

(reg-event-fx
 :initialize-db
 (fn []
   {:db db/default-db
    :fx [[:dispatch-later [{:ms 300 :dispatch [:init-web3]}]]]}))

(defn dispatch-time-in-seconds
  []
  (dispatch [:update-time (utils/now-in-seconds)]))

(defonce start-timer (js/setInterval dispatch-time-in-seconds 1000))

(reg-event-db
 :update-time
 (fn [db [_ time]]
   (assoc db :time time)))


;; Ethereum / Web3
;;

(reg-event-fx
 :init-web3
 [(inject-cofx :with-provider) (inject-cofx :with-signer)]
 (fn [{:keys [provider signer db]}]
   {:db (-> db
            (assoc-in [:ethers :provider] provider)
            (assoc-in [:ethers :signer] signer))
    :fx [[:dispatch [:get-network]]
         [:dispatch [:list-accounts]]]}))

(reg-event-fx
 :get-network
 (fn [{:keys [db]}]
   {:fx [[:ethers/get-network {:provider (get-in db [:ethers :provider])
                               :on-success [:get-network-success]
                               :on-failure [:get-network-failure]}]]}))

(reg-event-db
 :get-network-success
 (fn [db [_ network-info]]
   (assoc-in db [:ethers :chain-id] (.-chainId network-info))))

(reg-event-fx
 :get-network-failure
 (fn []
   {:fx [[:dispatch [:notify :get-network-error]]]}))

(reg-event-fx
 :list-accounts
 (fn [{:keys [db]}]
   {:fx [[:ethers/list-accounts {:provider   (get-in db [:ethers :provider])
                                 :on-success [:list-accounts-success]
                                 :on-failure [:list-accounts-failure]}]]}))

(reg-event-fx
 :list-accounts-success
 (fn [{:keys [db]} [_ addresses]]
   {:db (-> db
            (assoc-in [:user :address] (first addresses))
            (assoc-in [:loading :web3] false))
    :fx [[:dispatch [:set-active-page :dashboard]]
         [:dispatch [:get-streams]]
         [:dispatch [:get-contract-abis]]]}))

(reg-event-fx
 :list-accounts-failure
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :web3] false)
    :fx [[:dispatch [:set-active-page :login]]
         [:dispatch [:notify :list-accounts-error]]]}))


;; Metamask
;;

(reg-event-fx
 :connect-metamask
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :metamask] true)
    :fx [[:metamask/connect {:on-success [:connect-metamask-success]
                             :on-failure [:connect-metamask-failure]}]]}))

(reg-event-fx
 :connect-metamask-success
  (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :metamask] false)
    :fx [[:dispatch [:init-web3]]]}))

(reg-event-fx
 :connect-metamask-failure
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :metamask] false)
    :fx [[:dispatch [:notify :metamask-error]]
         [:dispatch [:set-active-page :login]]]}))


;; Interface
;;

(reg-event-fx
 :notify
 (fn [{:keys [db]} [_ event]]
   {:db (update db :notifications conj event)
    :fx [[:dispatch-later [{:ms 3000 :dispatch [:clear-notification event]}]]]}))

(reg-event-fx
 :clear-notification
 (fn [{:keys [db]} [_ event]]
   {:db (update db :notifications disj event)}))

(reg-event-db
 :open-new-stream-modal
 (fn [db _]
   (assoc-in db [:modal :new-stream] true)))

(reg-event-db
 :close-new-stream-modal
 (fn [db _]
   (assoc-in db [:modal :new-stream] false)))

(reg-event-db
 :toggle-burger-menu
 (fn [db _]
   (assoc db :burger-menu (not (:burger-menu db)))))


;; Sablier / Contracts / Streams
;;

(reg-event-fx
 :get-contract-abis
 (fn []
   {:fx [[:http-xhrio [{:method          :get
                        :uri             "/contracts/sablier.json"
                        :response-format (ajax/json-response-format)
                        :on-success      [:init-contract :sablier]
                        :on-failure      [:get-contract-abi-failure]}
                       {:method          :get
                        :uri             "/contracts/dai.json"
                        :response-format (ajax/json-response-format)
                        :on-success      [:init-contract :dai]
                        :on-failure      [:get-contract-abi-failure]}]]]}))

(reg-event-fx
 :get-contract-abi-failure
 (fn []
   {:fx [[:dispatch [:notify :get-contract-error]]]}))

(reg-event-fx
 :init-contract
 (fn [{:keys [db]} [_ contract-name contract-abi]]
   {:fx [[:ethers/init-contract {:address    (get-in db [:contracts contract-name :address])
                                 :abi        contract-abi
                                 :signer     (get-in db [:ethers :signer])
                                 :on-success [:init-contract-success contract-name]}]]}))

(reg-event-db
 :init-contract-success
 (fn [db [_ contract-name contract]]
   (assoc-in db [:contracts contract-name :instance] contract)))

(reg-event-fx
 :get-streams
 (fn [{:keys [db]}]
   (let [address      (get-in db [:user :address])
         subgraph-url (get-in db [:contracts :sablier :subgraph-url])]
     {:db (-> db
             (assoc-in [:loading :inbound-streams] true)
             (assoc-in [:loading :outbound-streams] true))
      :fx [[:http-xhrio [{:method          :post
                          :uri             subgraph-url
                          :params          {:query (sablier/streams-by-sender address)}
                          :format          (ajax/json-request-format)
                          :response-format (ajax/json-response-format {:keywords? true})
                          :on-success      [:get-streams-success :outbound]
                          :on-failure      [:get-streams-failure :outbound]}
                         {:method          :post
                          :uri             subgraph-url
                          :params          {:query (sablier/streams-by-recipient address)}
                          :format          (ajax/json-request-format)
                          :response-format (ajax/json-response-format {:keywords? true})
                          :on-success      [:get-streams-success :inbound]
                          :on-failure      [:get-streams-failure :inbound]}]]]})))

(reg-event-db
 :get-streams-success
 (fn [db [_ type response]]
   (let [streams     (get-in response [:data :streams])
         stream-type (keyword (str (name type) "-streams"))]
     (-> db
         (assoc-in [:user stream-type] (utils/->kebab-kws streams))
         (assoc-in [:loading stream-type] false)))))

(reg-event-fx
 :get-streams-failure
 (fn [{:keys [db]} [_ type]]
   (let [stream-type (keyword (str (name type) "-streams"))]
     {:db (assoc-in db [:loading stream-type] false)
      :fx [[:dispatch [:notify :get-streams-error]]]})))

(reg-event-fx
 :approve-create-stream
 (fn [{:keys [db]} [_ stream-data]]
   {:db (assoc-in db [:loading :stream-form] true)
    :fx [[:sablier/approve-spending {:erc20-contract  (get-in db [:contracts :dai :instance])
                                     :spender-address (get-in db [:contracts :sablier :address])
                                     :amount          (:amount stream-data)
                                     :on-success      [:approve-create-stream-success stream-data]
                                     :on-failure      [:approve-create-stream-failure]}]]}))

(reg-event-fx
 :approve-create-stream-success
  (fn [_ [_ stream-data]]
    {:fx [[:dispatch [:notify :approve-success]]
          [:dispatch [:create-stream stream-data]]]}))

(reg-event-fx
 :approve-create-stream-failure
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :stream-form] false)
    :fx [[:dispatch [:notify :approve-error]]]}))

(reg-event-fx
 :create-stream
 [(inject-cofx :with-stream-time-intervals) (inject-cofx :with-adjusted-deposit)]
 (fn [{:keys [db start-time stop-time deposit]} [_ stream-data]]
   {:fx [[:sablier/create-stream {:contract      (get-in db [:contracts :sablier :instance])
                                  :recipient     (:recipient stream-data)
                                  :deposit       deposit
                                  :token-address (get-in db [:contracts :dai :address])
                                  :start-time    start-time
                                  :stop-time     stop-time
                                  :on-success    [:create-stream-success]
                                  :on-failure    [:create-stream-failure]}]]}))

(reg-event-fx
 :create-stream-success
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :stream-form] false)
    :fx [[:dispatch [:notify :create-stream-success]]
         [:dispatch-later {:ms 2000 :dispatch [:close-new-stream-modal]}]
         [:dispatch-later {:ms 2000 :dispatch [:get-streams]}]]}))

(reg-event-fx
 :create-stream-failure
 (fn [{:keys [db]}]
   {:db (assoc-in db [:loading :stream-form] false)
    :fx [[:dispatch [:close-new-stream-modal]]
         [:dispatch [:notify :create-stream-error]]]}))


;; Routing
;;

(reg-event-fx
 :set-active-page
 (fn [{:keys [db]} [_ page {:keys [stream-id] :as params}]]
   (let [loading-web3? (get-in db [:loading :web3])
         provider      (get-in db [:ethers :provider])
         address       (get-in db [:user :address])
         new-page      (assoc db :active-page page)]
     (cond
       loading-web3?           {:db db
                                :fx [[:dispatch-later {:ms 100 :dispatch [:set-active-page page params]}]]} ; re-dispatch routing attempt whenever web3 resources are still loading
       (nil? provider)         {:db (assoc db :active-page :no-metamask)}
       (nil? address)          {:db (assoc db :active-page :login)}
       (= :stream-detail page) {:db (assoc new-page :active-stream stream-id)}
       :else                   {:db new-page}))))

