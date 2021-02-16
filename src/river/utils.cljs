(ns river.utils
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]))

(defn ->kebab-kws
  [m]
  (cske/transform-keys csk/->kebab-case-keyword m))

(defn now-in-seconds
  []
  (.floor js/Math (/ (.now js/Date) 1000)))

(defn seconds->locale-string
  [time-in-seconds]
  (.toLocaleString (new js/Date (* 1000 time-in-seconds))))
