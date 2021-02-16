(ns river.core
  (:require
   [day8.re-frame.http-fx]
   [reagent.dom   :as rdom]
   [re-frame.core :as rf]
   [river.events]
   [river.lib.ethers]
   [river.lib.sablier]
   [river.router  :as router]
   [river.subs]
   [river.views   :as views]))


(defn mount! []
  (rf/clear-subscription-cache!)
  (rdom/render [views/app]
    (js/document.getElementById "app")))

(defn ^:export init []
  (router/start!)
  (rf/dispatch-sync [:initialize-db])
  (mount!))
