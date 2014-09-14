(ns shepherd-examples.basic-http
  (:require [shepherd.ring.workflow.http-basic :as shepherd]
            [shepherd.ring.middleware :refer [wrap-auth]]
            [shepherd.password :refer [bcrypt check-bcrypt]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [not-found]]
            [compojure.handler :refer [api]]
            [hiccup.core :refer [html]]))


;;
;;  Database
;;


(def db
  {"admin" {:username "admin"
            :password (bcrypt "admin")
            :role :special}
   "bob" {:username "bob"
          :password (bcrypt "nob")
          :role :unremarkable}})

;;
;;  Interface
;;


(defn home-view
  []

  (html [:h1 "Home"]
        [:p "This page is not secured."]
        [:p "But "
            [:a {:href "/secured"} "this very interesting page"]
            " is."]))


(defn secured-view
  []

  (html [:h1 "Secured"]
        [:p "You made it to the secured page!"]
        [:p "You must be a very special person... unlike that Bob fellow."]))


;;
;;  Routes and Apps
;;


(defroutes routes
  (GET "/" [] (home-view))
  (GET "/secured" [] (secured-view))
  (not-found "I believe you might be lost."))


(defn authn
  [{:keys [username password]}]

  (when-let [user (get db username)]
    (when (check-bcrypt password (:password user))
      (dissoc user :password))))


(defn authr
  [request identity]

  (if (= "/secured" (:uri request))
    (when (= :special (:role identity))
      true)
    true))


(def app
  (let [workflow (shepherd/create-http-basic-workflow
                  {:authn authn
                   :authr authr})]
    (-> routes
        (wrap-auth workflow)
        (api))))
