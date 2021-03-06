
(ns edn-finder.main
  (:require [respo.core
             :refer
             [render! clear-cache! falsify-stage! render-element gc-states!]]
            [edn-finder.comp.container :refer [comp-container]]
            [cljs.reader :refer [read-string]]
            [cljs.core.async :refer [<!]]
            [cljs-http.client :as http])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce store-ref (atom {:data nil}))

(defn dispatch! [op op-data]
  (println "Action:" op (type op-data))
  (go
   (let [new-store (case op
                     :load-data
                       (let [response (<! (http/get op-data {:with-credentials? false}))]
                         (if (= 200 (:status response))
                           (let [content (:body response)]
                             (assoc @store-ref :data (read-string content)))))
                     @store-ref)]
     (reset! store-ref new-store))))

(defonce states-ref (atom {}))

(defn render-app! []
  (let [target (.querySelector js/document "#app")]
    (render! (comp-container @store-ref) target dispatch! states-ref)))

(def ssr-stages
  (let [ssr-element (.querySelector js/document "#ssr-stages")
        ssr-markup (.getAttribute ssr-element "content")]
    (read-string ssr-markup)))

(defn -main! []
  (enable-console-print!)
  (if (not (empty? ssr-stages))
    (let [target (.querySelector js/document "#app")]
      (falsify-stage!
       target
       (render-element (comp-container @store-ref ssr-stages) states-ref)
       dispatch!)))
  (render-app!)
  (add-watch store-ref :gc (fn [] (gc-states! states-ref)))
  (add-watch store-ref :changes render-app!)
  (add-watch states-ref :changes render-app!)
  (println "app started!"))

(defn on-jsload! [] (clear-cache!) (render-app!) (println "code update."))

(set! (.-onload js/window) -main!)
