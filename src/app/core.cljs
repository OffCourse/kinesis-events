(ns app.core
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [<! chan >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def AWS (node/require "aws-sdk"))

(node/enable-util-print!)
;; const payload = new Buffer(record.kinesis.data, 'base64').toString('ascii');

(defn convert-payload [data]
  (-> js/JSON
      (.parse (.toString (js/Buffer. data "base64") "ascii"))
      (js->clj :keywordize-keys true)))

(defn ^:export handler [event context cb]
  (let [event (js->clj event :keywordize-keys true)
        urls (map #(-> %
                       first
                       second
                       :data
                       convert-payload
                       :url)
                  (:Records event))]
    (go
      (cb nil (clj->js urls)))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
