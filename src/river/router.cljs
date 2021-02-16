(ns river.router
  (:require
   [bidi.bidi :as bidi]
   [pushy.core :as pushy]
   [re-frame.core :as rf]))

(def routes
  ["/" {""            :dashboard
        "login"       :login
        "no-metamask" :no-metamask
        "stream/"     {[:stream-id] :stream-detail}}])

(def history
  (let [dispatch #(rf/dispatch [:set-active-page (:handler %) {:stream-id (get-in % [:route-params :stream-id])}])
        match    #(bidi/match-route routes %)]
    (pushy/pushy dispatch match)))

(defn start!
  []
  (pushy/start! history))

(def url-for (partial bidi/path-for routes))
