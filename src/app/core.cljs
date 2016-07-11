(ns app.core
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [<! chan >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def AWS (node/require "aws-sdk"))
(def expander (node/require "unshortener"))
(def Kinesis (new AWS.Kinesis))

(node/enable-util-print!)

(defn convert-payload [data]
  (-> js/JSON
      (.parse (.toString (js/Buffer. data "base64") "ascii"))
      (js->clj :keywordize-keys true)))

(defn extract-payload [event]
  (-> (:Records event)
      first
      first
      second
      :data))

(defn expand-url [res]
  (let [{:keys [protocol hostname pathname] :as url} (js->clj (->> res
                                                   (.stringify js/JSON)
                                                   (.parse js/JSON))
                                             :keywordize-keys true)]
    (str protocol "//" hostname pathname)))

(defn extract-url [{:keys [url] :as record}]
  (let [c (chan)]
    (.expand expander url #(go (if %1
                                 (println %1)
                                 (>! c (expand-url %2)))))
    c))

(defn create-message [link]
  {:Data (.stringify js/JSON (clj->js link))
   :StreamName "expanded-links"
   :PartitionKey "url"})

(defn send-message [msg]
  (.putRecord Kinesis (clj->js msg) #(if %1
                                       (println %1)
                                       (println %2))))

(defn ^:export handler [event context cb]
  (go
    (let [event (js->clj event :keywordize-keys true)
          payload (-> event
                      (extract-payload)
                      (convert-payload))
          url         (<! (extract-url payload))
          record      (assoc payload :url url)
          message     (create-message record)]
      (println (.stringify js/JSON (clj->js record)))
      (send-message message)
      (cb nil (clj->js message)))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
