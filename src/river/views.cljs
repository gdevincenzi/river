(ns river.views
  (:require
   [reagent.core     :as reagent]
   [re-frame.core    :as rf]
   [river.lib.ethers :as ethers]
   [river.router     :refer [url-for]]))


(defn notification
  [name type message]
  [:div.notification.is-light {:class (if (= :error type) "is-danger" "is-success")}
   [:button.delete {:on-click #(rf/dispatch [:clear-notification name])}]
   message])

(defn get-notification
  [name]
  (case name
    :metamask-error        [notification name :error "Failed to connect to metamask"]
    :list-accounts-error   [notification name :error "Failed to get user accounts. Please try reconnecting Metamask."]
    :get-contract-error    [notification name :error "Failed to get contract ABIs."]
    :get-network-error     [notification name :error "Failed to get Ethereum Network information."]
    :approve-error         [notification name :error "Failed to get user approval."]
    :approve-success       [notification name :success "Approval tx confirmed."]
    :get-streams-error     [notification name :error "Failed to get streams."]
    :create-stream-success [notification name :success "Stream creation tx confirmed. It could take some moments to show up on your dashboard."]
    :create-stream-error   [notification name :error "Failed to create new stream."]
    nil))

(defn notifications-display
  [notifications]
  [:div.column.notifications
   (for [n notifications]
     ^{:key n} [get-notification n])])

(defn chain-id-warning
  [chain-id]
  (when (and (some? chain-id)
             (not= 4 chain-id))
    [:div.notification.is-danger.has-text-centered
     [:p "WARNING: Not connected to Rinkeby network."]
     [:p "The app currenly only supports the Rinkeby testnet."]
     [:p "Please connect to Rinkeby and reload the page."]]))

(defn with-hero
  [content]
  [:section.hero.is-fullheight
   [:div.hero-head]
   [:div.hero-body
    [:div.container
     [:div.columns.is-vcentered
      [:div.column.has-text-centered-mobile.is-narrow.mb-6
       [:p.title "River"]
       [:p.subtitle "Payments, streaming"]
       content]
      [:div.column
       [:div.block.has-text-centered-mobile
        [:img.hero-logo {:src "/img/river.png"}]]]]]
    [:div.hero-foot]]])

(defn header
  []
  (let [address @(rf/subscribe [:selected-address])
        burger-menu @(rf/subscribe [:burger-menu])]
    [:nav.navbar
     [:div.navbar-brand
      [:div.navbar-item
       [:img {:src "/img/river.png" :width 114 :height 28}]]
      [:a.navbar-burger {:class (when burger-menu "is-active")
                         :on-click #(rf/dispatch [:toggle-burger-menu])}
       [:span] ; the 3 spans are necessary for Bulma to render the menu correctly
       [:span]
       [:span]]]
     [:div.navbar-menu {:class (when burger-menu "is-active")}
      [:div.navbar-start
       [:a.navbar-item {:href (url-for :dashboard)}
        "Dashboard"]]
      [:div.navbar-end
       [:div.navbar-item
        [:button.button.river-blue {:on-click #(rf/dispatch [:open-new-stream-modal])}
         "Create new stream"]]
       [:div.navbar-item
        [:div "Hello " address]]]]]))

;; Stream
;;

(defn stream-status-tag
  [status]
  (case status
    :finished [:span.tag.is-success.is-light "Finished"]
    :queued   [:span.tag.is-warning.is-light "Queued"]
    :canceled [:span.tag.is-danger.is-light "Canceled"]
    [:span.tag.is-info.is-light "Ongoing"]))

(defn stream-list-item
  [type stream]
  [:a.panel-block {:href (url-for :stream-detail :stream-id (:id stream))}
   [:div.column
    [:div.columns.is-vcentered
     [:div.column
      [:p.title.is-size-6
       (if (= :inbound type)
         (str "From: " (:sender stream))
         (str "To: " (:recipient stream)))]
      [:p.subtitle.is-size-7
       (str "Value: " (:deposit stream))]]
     [:div.column.is-narrow.has-text-right.has-text-left-mobile
      [stream-status-tag (:status stream)]
      [:p.is-size-7 "Ends on: " (:ends-on stream)]]]
    [:progress.progress.is-tiny {:max 100 :value (:progress stream)}]]])

(defn stream-panel
  [{:keys [title type streams loading?]}]
  [:div.panel
   [:div.panel-heading.river-blue
    [:div title]]
   (if loading?
     [:div.p-6
      [:div.spinner]]
     (for [stream streams]
       ^{:key (:id stream)} [stream-list-item type stream]))])

(defn new-stream
  []
  (let [form-data  (reagent/atom {:recipient "" :amount "" :duration ""})
        validation (reagent/atom {:recipient true :amount true :duration true})]
    (fn []
      (let [{:keys [recipient amount duration]} @form-data
            valid-address?                      (:recipient @validation)
            valid-amount?                       (:amount @validation)
            valid-duration?                     (:duration @validation)
            loading                             @(rf/subscribe [:loading])
            selected-address                    @(rf/subscribe [:selected-address])
            approve-create-stream               (fn [event form-data]
                                                  (.preventDefault event)
                                                  (rf/dispatch [:approve-create-stream form-data]))]
        [:div.modal.is-active
         [:div.modal-background {:on-click #(rf/dispatch [:close-new-stream-modal])}]
         [:div.modal-card
          [:form {:on-submit #(approve-create-stream % @form-data)}
           [:header.modal-card-head.river-blue
            [:p.modal-card-title.has-text-white "Create new Stream"]
            [:button.delete {:on-click #(rf/dispatch [:close-new-stream-modal])}]]
           [:section.modal-card-body
            [:div.field
             [:div.label "Recipient Address"]
             [:div.control
              [:input.input {:type       "text"
                             :value      recipient
                             :class      (when-not valid-address? "is-danger")
                             :auto-focus true
                             :on-change  #(swap! form-data assoc :recipient (-> % .-target .-value))
                             :on-blur    #(swap! validation assoc :recipient (and (not= selected-address (-> % .-target .-value))
                                                                                  (ethers/valid-address? (-> % .-target .-value))))}]
              (when-not valid-address?
                [:p.help.is-danger "Invalid address"])]]

            [:div.label "Amount to Stream"]
            [:div.field.has-addons
             [:p.control.is-expanded
              [:input.input {:type      "text"
                             :value     amount
                             :class     (when-not valid-amount? "is-danger")
                             :on-change #(swap! form-data assoc :amount (-> % .-target .-value))
                             :on-blur   #(swap! validation assoc :amount (some? (re-matches #"\d+" (-> % .-target .-value))))}]]
             [:p.control
              [:a.button.is-static "DAI"]]]
            (when-not valid-amount?
              [:p.help.is-danger "Invalid amount"])

            [:div.label "Over how many hours?"]
            [:div.field.has-addons
             [:p.control.is-expanded
              [:input.input {:type      "text"
                             :value     duration
                             :class     (when-not valid-duration? "is-danger")
                             :on-change #(swap! form-data assoc :duration (-> % .-target .-value))
                             :on-blur   #(swap! validation assoc :duration (some? (re-matches #"\d+" (-> % .-target .-value))))}]]
             [:p.control
              [:a.button.is-static "hours"]]]
            (when-not valid-duration?
              [:p.help.is-danger "Invalid duration"])]
           [:footer.modal-card-foot
            [:button.button.river-blue
             {:type     "submit"
              :disabled (when-not (every? true? (vals @validation)) true)
              :class    (when (:stream-form loading) "is-loading")}
             "Create Stream"]
            [:div
             [:p.is-size-7 "Your approval will be required before creating the stream."]
             [:p.is-size-7 "The transactions will be sent to the blockchain and confirmation will take place."]
             [:p.is-size-7 "This proccess can take a few moments."]]]]]]))))


;; Pages
;;

(defn loading-splash
  []
  [:section.hero.is-fullheight
   [:div.hero-head]
   [:div.hero-body
    [:div.container
     [:div.columns.is-centered.is-mobile
      [:div.column.has-text-centered.is-narrow.is-italic.has-text-weight-light
       [:p "The cold stream"]
       [:p "holds the moon"]
       [:p "clarity"]
       [:p "like a mirror."]
       [:br]
       [:p "- Bái Jūyì"]
       [:progress.progress.is-info.is-tiny.mt-6]]]]]
   [:div.hero-footer]])

(defn stream-detail
  []
  (let [stream @(rf/subscribe [:stream-detail])
        loading @(rf/subscribe [:loading])]
    [:div
     [header]
     [:section.section
      [:div.container
       (if (or (:inbound-streams loading)
               (:outbound-streams loading))

         [:div.columns.is-centered
          [:div.column.is-half
           [:div.p-6
            [:div.spinner]]]]

         [:div.columns.is-centered
          [:div.column.is-half
           [:div.panel
            [:div.panel-heading.river-blue
             [:div (str "Details for Stream " (:id stream))]
             [stream-status-tag (:status stream)]]
            [:div.panel-block
             [:div.column
              [:p.title.is-size-6 "From: " (:sender stream)]
              [:p.title.is-size-6 "To: " (:recipient stream)]]]
            [:div.panel-block
             [:div.column
              [:p.is-size-6 [:strong "Starts at: "] (:starts-on stream)]
              [:p.is-size-6 [:strong "Ends at: "] (:ends-on stream)]]]
            [:div.panel-block
             [:div.column
              [:p.is-size-6 [:strong "Total deposited: "] (:deposit stream)]
              [:p.is-size-6 [:strong "Amount Streamed: "] (:amount-transfered stream)]]]
            [:div.panel-block
              [:div.column
               [:p.mx-2.has-text-centered (str (:progress stream) "% Streamed.")]
               [:progress.progress.is-small.mx-2 {:max 100 :value (:progress stream)}]]]
            [:div.panel-block
             [:div.column
              [:a.button.river-blue {:href (url-for :dashboard)} "Back"]]]]]])]]]))

(defn dashboard
  []
  (let [inbound          @(rf/subscribe [:recent-in-streams])
        outbound         @(rf/subscribe [:recent-out-streams])
        loading          @(rf/subscribe [:loading])
        chain-id         @(rf/subscribe [:chain-id])]
    [:div
     [header]
     [:section.section
      [:div.container

       [chain-id-warning chain-id]

       [:div.columns
        [:div.column
         [stream-panel {:title    "Inbound Streams"
                        :type     :inbound
                        :streams  inbound
                        :loading? (:inbound-streams loading)}]]
        [:div.column
         [stream-panel {:title    "Outbound Streams"
                        :type     :outbound
                        :streams  outbound
                        :loading? (:outbound-streams loading)}]]]]]]))

(defn metamask-not-found
  []
  [with-hero
   [:div
    [:p "It seems Metamask is not installed, and it is required"]
    [:p "Please " [:a {:href "https://metamask.io/download.html"} "download it"] " and try again"]]])

(defn login
  []
  (let [loading @(rf/subscribe [:loading])]
    [with-hero
     [:button.button.river-blue {:class (when (:metamask loading) "is-loading")
                                 :on-click #(rf/dispatch [:connect-metamask])}
      "Connect with Metamask"]]))


;; App
;;

(defn pages [page-name]
  (case page-name
    :splashscreen  [loading-splash]
    :no-metamask   [metamask-not-found]
    :login         [login]
    :stream-detail [stream-detail]
    :dashboard     [dashboard]))

(defn app
  []
  (let [active-page @(rf/subscribe [:active-page])
        notifications @(rf/subscribe [:notifications])
        new-stream-modal @(rf/subscribe [:new-stream-modal])]
    [:div
     (when new-stream-modal [new-stream])
     [pages active-page]
     [notifications-display notifications]]))
