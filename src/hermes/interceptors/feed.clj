(ns hermes.interceptors.feed
  (:require [io.pedestal.service.interceptor :as i]
             [hermes.entities.feed :as f]
             [ring.util.response :as ring-resp]
             [hermes.db :as db]
             [hermes.entities.feed :as feed]
             [clojure.java.io :as io])
  (:import (java.io FileInputStream File)))

(def find-schema
  {:feed-id java.util.UUID
   :account-id java.util.UUID})

(i/defbefore find-feeds
  [ctx]
  (assoc-in ctx [:request :feeds] (feed/list-feeds (get-in ctx [:request :database]) (get-in ctx [:request :account :account_id]))))

(defn find-by-id
  [request-key-path]
  (i/interceptor
   :enter (fn [ctx]
            (let [account-id (get-in ctx [:request :account :account_id])
                  id (get-in ctx (concat [:request] request-key-path))
                  db (get-in ctx [:request :database])]
              (if-let [feed (feed/find-by-id db account-id id)]
                (assoc-in ctx [:request :feed] feed)
                (ring-resp/not-found "Feed not found"))))))

(i/defhandler create
  [request]
  (let [params (:params request)
        database (:database request)
        account-id (get-in request [:account :account_id])
        filename (get-in params ["datafile" :filename])
        input-file (get-in params ["datafile" :tempfile])]
    (if-let [feed (feed/create-feed database account-id filename)]
      (do
        (let [dir (str "/tmp/" (:feed_id feed))
              file (str dir "/" filename)]
          (.mkdir (File. dir))
          (io/copy input-file (File. file)))
        (ring-resp/redirect-after-post (str "/feeds/" (:feed_id feed))))
      (ring-resp/not-found "Could not create feed"))))

(i/defhandler download
  [request]
  (let [feed (:feed request)
        filepath (str "/tmp/" (:feed_id feed) "/" (:filename feed))]
    (-> (ring-resp/file-response filepath)
        (ring-resp/header "Content-Disposition" (str "attachment; filename=" (:filename feed))))))
