(ns comms.core
  (:require [clojure.core.async :as a]
            [catacumba.serializers :as sz]
            [catacumba.impl.websocket :as implws]
            [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as b64]))

(defn- normalize-frame
  [frame]
  (if (map? frame)
    (let [frame (transient frame)]
      (when-not (:type frame)
        (assoc! frame :type :response))
      (persistent! frame))
    (throw (ex-info "Invalid response format" {}))))

(defn- connect-chans
  "Like core.async pipe but reacts on close
  in both sides."
  [from to]
  (a/go-loop []
    (let [v (a/<! from)]
      (if (nil? v)
        (a/close! to)
        (if (a/>! to v)
          (recur)
          (a/close! from)))))
  to)

(defn frame
  "Creates a frame"
  ([data]
   {:type :response :data data})
  ([type data]
   {:type type :data data}))

(defn decode-query-params
  [context]
  (-> (get-in context [:query-params :d])
      (or (byte-array 0))
      (b64/decode)
      (sz/decode :transit+json)))

(defn socket
  "Starts a websocket connection. Returns a tuple of
  [streamin streamout]"
  [context handler]
  (letfn [(encode-message [msg]
            (-> (normalize-frame msg)
                (sz/encode :transit+json)
                (codecs/bytes->str)))

          (decode-message [msg]
            (-> (codecs/str->bytes msg)
                (sz/decode :transit+json)))

          (inner-handler [{:keys [in out ctrl] :as context}]
            (let [out-xf (map encode-message)
                  in-xf (comp (map decode-message)
                              (filter #(not= (:type %) :ping)))
                  out' (a/chan 1 out-xf)
                  in' (a/chan 1 in-xf)]

              (connect-chans out' out)
              (connect-chans in in')

              ;; keep-alive loop
              (a/go-loop [n 1]
                (a/<! (a/timeout 5000))
                (when (a/>! out' {:type :ping :n n})
                  (recur (inc n))))

              (-> context
                  (assoc :out out' :in in'
                         :query-params (merge (:query-params context)
                                              {:d (decode-query-params context)}))
                  (handler))))]
    (implws/websocket context inner-handler)))
