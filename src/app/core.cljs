(ns app.core
  (:require [cljs.nodejs :as node]
            [cljs.core.async :refer [<! chan >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def AWS (node/require "aws-sdk"))
(def expander (node/require "unshortener"))
#_(def expander (node/require "expand-url"))
(def exp (node/require "node-url-expand"))

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
  (let [{:keys [hostname pathname] :as url} (js->clj (->> res
                                                   (.stringify js/JSON)
                                                   (.parse js/JSON))
                                             :keywordize-keys true)]
    (str hostname pathname)))

(defn extract-url [{:keys [url] :as record}]
  (let [c (chan)]
    (.expand expander url #(go (if %1
                                 (println %1)
                                 (>! c (expand-url %2)))))
    c))

(defn ^:export handler [event context cb]
  (go
    (let [event (js->clj event :keywordize-keys true)
          payload (-> event
                      (extract-payload)
                      (convert-payload))
          url     (<! (extract-url payload))
          url-string (.stringify js/JSON (clj->js {:url url}))]
      (println url-string)
      (cb nil url-string))))

(defn -main [] identity)
(set! *main-cli-fn* -main)
