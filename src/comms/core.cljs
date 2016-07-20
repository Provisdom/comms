(ns comms.core
  (:require [cognitect.transit :as t]
            [promesa.core :as p]
            [beicon.core :as s]
            [goog.crypt.base64 :as b64]
            [goog.events :as events]
            [httpurr.client :as http]
            [httpurr.status :as http-status]
            [httpurr.client.xhr :as xhr])
  (:import [goog.net WebSocket]
           [goog.net.WebSocket EventType]
           [goog.Uri QueryData]
           [goog Uri Timer]))

(def ^:private
+default-headers+
  {"content-type" "application/transit+json"})

(defn decode
  [data]
  (let [r (t/reader :json {:handlers {"u" ->UUID}})]
    (t/read r data)))

(defn encode
  [data]
  (let [w (t/writer :json)]
    (t/write w data)))

(defn- prepare-request
  ([client data]
   (prepare-request client data (:method client) {}))
  ([client data method params]
   (let [req {:headers (merge +default-headers+ @(:headers client))
              :method  method
              :url     (:url client)}]
     (let [pd (.clone (:params client))]
       (when-not (empty? params)
         (.extend pd (clj->js params)))
       (.set pd "d" (b64/encodeString data true))
       (merge req {:query-string (.toString pd)})))))

(defrecord Client [headers method url params])

(defn client
  "Creates a new client instance from socket."
  ([url]
   (client url {}))
  ([url {:keys [headers method params]
         :or   {headers {} method :put params {}}}]
   (let [paramsdata (QueryData.)]
     (.extend paramsdata (clj->js params))
     (Client. (atom headers) method url paramsdata))))

(defn socket
  "Sends a :socket frame to the server and opens
  bi-direction websocket connection.

  This function returns a vector of two streams,
  first for input messages and second for output.

  You can close the socket just ending the output
  stream."
  ([client]
   (socket client nil nil))
  ([client data]
   (socket client data nil))
  ([client data {:keys [params _type] :or {params {} _type :socket}}]
   (let [frame (encode {:data data :type _type})
         req (prepare-request client frame :get params)
         uri (Uri. (:url req))]
     (.setQuery uri (:query-string req))
     (.setScheme uri (if (= (.getScheme uri) "http") "ws" "wss"))
     (let [ws (WebSocket. false)
           timer (Timer. 5000)
           busin (s/bus)
           streamin (s/filter #(not= (:type %) :ping) busin)
           busout (s/bus)]
       (letfn [(on-ws-message [event]
                 (let [frame (decode (.-message event))]
                   (s/push! busin frame)))
               (on-ws-error [event]
                 (s/error! busin event)
                 (.close ws))
               (on-ws-closed [event]
                 (s/end! busout)
                 (s/end! busin))
               (on-timer-tick [_]
                 ;(println "t msg")
                 (let [frame {:type :ping}]
                   (s/push! busout frame)))
               (on-busout-value [msg]
                 (let [data (encode msg)]
                   (.send ws data)))
               (on-busout-end []
                 (.close ws))]

         (s/on-end busout on-busout-end)
         (s/on-value busout on-busout-value)

         (events/listen ws EventType.MESSAGE on-ws-message)
         (events/listen ws EventType.ERROR on-ws-error)
         (events/listen ws EventType.CLOSED on-ws-closed)
         (events/listen timer Timer.TICK on-timer-tick)

         (.start timer)
         (.open ws (.toString uri))

         [streamin busout])))))

(defn subscribe
  "Sends a :subscribe frame to the server and open an
  uni-directional websocket connection.

  This function returns one stream for read messages
  from backend. You can close the subscription sockets
  just ending the stream."
  ([client]
   (subscribe client nil nil))
  ([client data]
   (subscribe client data nil))
  ([client data {:keys [params] :or {params {}} :as opts}]
   (let [[in out] (socket client data (assoc opts :_type :subscribe))
         bus (s/bus)]
     (s/subscribe in #(s/push! bus %) #(s/error! bus %) #(s/end! bus))
     (s/on-end bus #(s/end! out))
     bus)))