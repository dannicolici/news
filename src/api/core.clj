(ns api.core
  (:require [domain.news :as news :refer :all]
            [domain.entities :as d :refer :all]
            [infrastructure.persistence :as p :refer :all]
            [infrastructure.auth :as auth]
            [infrastructure.password :as pass]
            [compojure.core :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as r :refer :all]
            [ring.middleware.json :refer [wrap-json-response]]))

(defn save-news [user-id text]
  (if (= 1 (p/save-news! (d/news nil user-id text)))
    (r/status {:body "created"} 201)
    (r/status {:body "user doesn't exist"} 409)))

(defroutes user-api
  (context "/api" []

    (POST "/user" [id fname lname pwd]
      (if (= 1 (p/save-user! (d/user id fname lname (pass/encrypt pwd))))
        (r/status {:body "created"} 201)
        (r/status {:body "already exists"} 409)))))

(defroutes news-api
           (context "/api" []

             (POST "/news/:user-id" [user-id text]
               (save-news user-id text))

             (POST "/news" [text :as request]
               (save-news (auth/get-current-username request) text))

             (GET "/news/:user-id" [user-id]
               (r/response
                 (news/news-for-user user-id)))

             (GET "/news" [:as {params :params}]
               (r/response
                 (if (empty? params)
                     (news/all-news)
                     (news/paginated-news (keyword (:sort-by params))
                                          (Integer/parseInt (:page-size params))
                                          (Integer/parseInt (:page params))))))))

(defroutes news-api-routes news-api)
(defroutes user-api-routes user-api)

(defroutes non-secure-app (->
                            (wrap-defaults (routes news-api-routes
                                                   user-api-routes)
                                           (assoc-in site-defaults [:security :anti-forgery] false))
                            wrap-json-response))

