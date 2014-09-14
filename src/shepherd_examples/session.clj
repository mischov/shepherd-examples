(ns shepherd-examples.session
  (:require [shepherd.ring.workflow.session :as shepherd]
            [shepherd.ring.middleware :refer [wrap-auth]]
            [shepherd.password :refer [bcrypt check-bcrypt]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [not-found]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
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


(defn login-view
  []

  (html [:form {:method "POST" :action "/login"}
         [:input {:type "text"
                  :name "username"
                  :placeholder "Username..."}]
         [:input {:type "password"
                  :name "password"
                  :placeholder "Password..."}]
         [:input {:type "submit"
                  :value "Login"}]]))


(defn attempt-login
  [request]

  (let [{:keys [username password]} (get request :params)
        session (get request :session)
        user (get db username)
        new-session (assoc session :identity (dissoc user :password))]
    (if (check-bcrypt password (:password user))
      (-> (redirect "/")
          (assoc :session new-session))
      (redirect "/login"))))


(defn logout-user
  []

  (-> (redirect "/")
      (assoc :session {})))


;;
;;  Routes and App
;;


(defroutes routes
  (GET "/" [] (home-view))
  (GET "/login" [] (login-view))
  (POST "/login" request (attempt-login request))
  (GET "/logout" [] (logout-user))
  (GET "/secured" [] (secured-view))
  (not-found "I believe you might be lost."))


(defn authr
  [request identity]

  (if (= "/secured" (:uri request))
    (when (= :special (:role identity))
      true)
    true))


(defn unauthr
  [request identity]

  (if identity
    {:status 403
     :body "Permission denied."}
    (redirect "/login")))


(def app
  (let [workflow (shepherd/create-session-workflow
                  {:authr authr
                   :unauthr unauthr})]
    (-> routes
        (wrap-auth workflow)
        (wrap-session)
        (api))))
