(ns api.functional-test
  (:require [api.core :refer :all]
            [infrastructure.password :refer :all]
            [infrastructure.persistence :as p :refer :all]
            [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [clojure.string :as str])
  (:import (redis.embedded RedisServer)))

(def redis-server (RedisServer. (Integer. 6379)))

(defn wrap-all-tests [f]
  (.start redis-server)
  (f)
  (.stop redis-server))

(defn data-fixture [f]
  (p/flush-db)
  (f)
  (p/flush-db))

(use-fixtures :once wrap-all-tests)
(use-fixtures :each data-fixture)

(defn create-user-and-assert-response [id]
  (let [response (non-secure-app (mock/request :post "/api/user" {"id" id, "fname" "john", "lname" "doe", "pwd" (encrypt "pwd")}))]
    (is (= (:status response) 201))
    (is (= (:body response) "created"))))

(defn create-news-and-assert-response [user-id news]
  (let [response (non-secure-app (-> (mock/request :post (str "/api/news/" user-id) news)))]
    (is (= (:status response) 201))
    (is (= (:body response) "created"))))

(deftest test-user
  (testing "put user"
    (create-user-and-assert-response "nickname"))

  (testing "get user"
    (let [user (p/get-user-by-id "nickname")]
      (is (= (:id user) "nickname"))
      (is (= (:first-name user) "john"))
      (is (= (:last-name user) "doe"))
      (is (matches "pwd" (:password user)))))

  (testing "put same user"
    (let [response (non-secure-app (mock/request :post "/api/user" {"id" "nickname", "fname" "john", "lname" "doe", "pwd" (encrypt "pwd")}))]
      (is (= (:status response) 409))
      (is (= (:body response) "already exists")))))

(deftest test-news
  (testing "post news for non-existing user"
    (let [response (non-secure-app (mock/request :post "/api/news/non-existing-user" {"text" "some news text"}))]
      (is (= (:status response) 409))))

  (testing "post news for existing user"
    (create-user-and-assert-response "zzz")
    (create-news-and-assert-response "zzz" {"text" "some news text"})
    (let [response (non-secure-app (mock/request :get "/api/news/zzz"))]
      (is (= (:status response) 200))
      (is (str/includes? (:body response) "{\"id\":\"1\",\"user-id\":\"zzz\",\"text\":\"some news text\"")))
    (create-news-and-assert-response "zzz" {"text" "news2"})
    (let [response (non-secure-app (mock/request :get "/api/news/zzz"))]
      (is (= (:status response) 200))
      (is (let [resp (:body response)]
            (and (str/includes? resp "{\"id\":\"1\",\"user-id\":\"zzz\",\"text\":\"some news text\"")
                 (str/includes? resp "{\"id\":\"2\",\"user-id\":\"zzz\",\"text\":\"news2\""))))))

  (testing "user in session can post news"
    ;we need to bypass ring's wrap-session to mock the session
    (let [request (mock/request :post "/api/news")
          session-request (assoc-in request [:session :cemerick.friend/identity :current] "zzz")
          params-request (assoc-in session-request [:params] {"text" "user in session"})
          response (api-routes params-request)]
      (is (= (:status response) 201))
      (is (= (:body response) "created")))
    (let [response (non-secure-app (mock/request :get "/api/news/zzz"))]
      (is (= (:status response) 200))
      (is (let [resp (:body response)]
            (and (str/includes? resp "{\"id\":\"1\",\"user-id\":\"zzz\",\"text\":\"some news text\"")
                 (str/includes? resp "{\"id\":\"2\",\"user-id\":\"zzz\",\"text\":\"news2\"")
                 (str/includes? resp "{\"id\":\"3\",\"user-id\":\"zzz\",\"text\":\"user in session\"")))))))