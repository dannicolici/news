(ns block.handler-test
  (:require [clojure.test :refer :all]
            [persistence.core :as p :refer :all]
            [ring.mock.request :as mock]
            [block.api :refer :all]
            [password.core :refer :all]
            [clojure.string :as str]))

(defn data-fixture [f]
  (p/flush-db)
  (f)
  (p/flush-db))

(use-fixtures :each data-fixture)

(defn create-and-assert-user [id]
  (let [response (non-secure-app (mock/request :put (str "/api/user/" id "/john/doe/" (md5 "pwd"))))]
    (is (= (:status response) 201))
    (is (= (:body response) "created"))))

(defn create-and-assert-news [user-id news]
  (let [response (non-secure-app (-> (mock/request :post (str "/api/news/" user-id) news)))]
    (is (= (:status response) 201))
    (is (= (:body response) "created"))))

(deftest test-user
  (testing "put user"
    (create-and-assert-user "nickname"))

  (testing "get user"
    (let [user (p/get-user-by-id "nickname")]
      (is (= (:id user) "nickname"))
      (is (= (:first-name user) "john"))
      (is (= (:last-name user) "doe"))
      (is (= (:password user) (md5 "pwd")))))

  (testing "put same user"
    (let [response (non-secure-app (mock/request :put (str "/api/user/nickname/john/doe/" (md5 "pwd"))))]
      (is (= (:status response) 409))
      (is (= (:body response) "already exists")))))

(deftest test-news
  (testing "post news for non-existing user"
    (let [response (non-secure-app (mock/request :post "/api/news/non-existing-user" {"text" "some news text"}))]
      (is (= (:status response) 409))))
  (testing "post news for existing user"
    (create-and-assert-user "zzz")
    (create-and-assert-news "zzz" {"text" "some news text"})
    (let [response (non-secure-app (mock/request :get "/api/news/zzz"))]
      (is (= (:status response) 200))
      (is (str/includes? (:body response) "{\"id\":\"1\",\"user-id\":\"zzz\",\"text\":\"some news text\"")))
    (create-and-assert-news "zzz" {"text" "news2"})
    (let [response (non-secure-app (mock/request :get "/api/news/zzz"))]
      (is (= (:status response) 200))
      (is (let [resp (:body response)]
            (and (str/includes? resp "{\"id\":\"1\",\"user-id\":\"zzz\",\"text\":\"some news text\"")
                 (str/includes? resp "{\"id\":\"2\",\"user-id\":\"zzz\",\"text\":\"news2\"")))))))
