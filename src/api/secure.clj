(ns api.secure
  (:require [api.core :as api]
            [infrastructure.persistence :as db]
            [compojure.core :refer :all]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-response]]
            [cemerick.friend [workflows :as workflows] [credentials :as creds]]
            [cemerick.friend :as friend]
            [compojure.route :as route]
            [hiccup.core :refer :all]))

(defn user [id]
  "Looks up a user by id and transforms it to a friend auth map
   with 'user' role"
  (let [usr (db/get-user-by-id id)]
    (hash-map :username (:id usr), :password (:password usr), :roles #{::user})))

(defn from-cljs [cljs-namespace page-title]
  "Uses main.js to inject content defined in cljs-namespace
   into a div DOM element, with id=root"
  (html [:head [:title page-title]]
        [:body
         [:div {:id "root"}]
         [:script {:src "js/main.js"}]
         [:script (str cljs-namespace ".start()")]]))

(defroutes app-routes
           api/api-routes
           (GET "/news" [] (from-cljs "news.core" "News"))
           (friend/logout (POST "/logout" [] "Logged out")))

(defroutes public-routes
           (GET "/" [] (from-cljs "menu.core" "Menu"))
           (GET "/login" [] (from-cljs "menu.login" "Login")))


(defroutes static-routes
           (route/resources "/public"))

(defroutes secured-app-routes
           (-> (wrap-routes app-routes
                            friend/wrap-authorize
                            #{::user})
               (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn user)
                                     :workflows     [(workflows/interactive-form)]})
               wrap-session
               wrap-params
               wrap-keyword-params))

(def app (->
           (routes
             static-routes
             public-routes
             (wrap-defaults secured-app-routes
                            (assoc-in site-defaults [:security :anti-forgery] false))
             (route/not-found "Not Found"))
           wrap-json-response))

(defn -main [& args]
  (db/init-db)
  (run-jetty #'app {:port 8080}))


