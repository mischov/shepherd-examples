(ns shepherd-examples.basic-http
  (:require [shepherd.ring.workflow.http-basic :refer [http-basic-workflow
                                                       parse-identity]]
            [shepherd.ring.middleware :refer [wrap-auth]]
            [shepherd.authorization :refer [throw-unauthorized]]
            [shepherd.password :refer [bcrypt check-bcrypt]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found]]
            [compojure.handler :refer [api]]
            [hiccup.core :refer [html]]))


;;  Database
(def db
  {"admin" {:username "admin"
            :password (bcrypt "admin")
            :role :special}
   "bob" {:username "bob"
          :password (bcrypt "nob")
          :role :unremarkable}})


;;  Interface
(def home-view
  (html [:h1 "Home"]
        [:p "This page is not secured."]
        [:p "But "
            [:a {:href "/secured"} "this very interesting page"]
            " is."]))


(def secured-view
  (html [:h1 "Secured"]
        [:p "You made it to the secured page!"]
        [:p "You must be a very special person... unlike that Bob fellow."]))


;;  Routes and Apps
(defroutes routes
  (GET "/" [] home-view)
  (GET "/secured" [] secured-view)
  (not-found "I believe you might be lost."))


(defn wrap-secure-secured
  "Ring handler that throws an Unauthorized exception if request
   is not authorized."
  [handler]

  (fn [request]
    (let [identity (parse-identity request)]
      (cond
       (not= (:uri request) "/secured") (handler request)
       (= (:role identity) :special) (handler request)
       :else (throw-unauthorized)))))


(defn authenticate
  "If credentials represent a valid identity, returns that identity."
  [{:keys [username password]}]

  (when-let [user (get db username)]
    (when (check-bcrypt password (:password user))
      (dissoc user :password))))


(def app
  (let [workflow (http-basic-workflow
                  {:authenticate authenticate})]
    (-> routes
        (wrap-secure-secured)
        (wrap-auth workflow)
        (api))))
