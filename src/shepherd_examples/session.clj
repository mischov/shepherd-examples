(ns shepherd-examples.session
  (:require [shepherd.ring.workflow.session :refer [session-workflow
                                                    parse-identity]]
            [shepherd.ring.workflow.form-login :refer [form-login-workflow]]
            [shepherd.ring.middleware :refer [wrap-auth wrap-authentication]]
            [shepherd.authorization :refer [throw-unauthorized]]
            [shepherd.password :refer [bcrypt check-bcrypt]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [not-found]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
            [hiccup.core :refer [html]]

            [criterium.core :refer [with-progress-reporting quick-bench]]))


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


(def login-view
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
  "When request contains an identity, adds identity to cookie.

   Otherwise throws an Unauthorized exception."
  [request]

  (let [identity (parse-identity request)
        session (get request :session)
        new-session (assoc session :identity identity)]
    (if identity
      (-> (redirect "/")
          (assoc :session new-session))
      (throw-unauthorized))))


(defn logout-user
  []

  (-> (redirect "/")
      (assoc :session {})))


;;  Routes and App
(defroutes routes
  (GET "/" [] home-view)
  (GET "/login" [] login-view)
  (POST "/login" request (attempt-login request))
  (GET "/logout" [] (logout-user))
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


(defn unauthenticated-session
  "Function to be called if request has no identity and an
   Unauthorized exception is thrown."
  [request]

  (redirect "/login"))


(defn authenticate-login
  "If credentials represent a valid identity, returns that identity."
  [{:keys [username password] :as credentials}]

  (when-let [user (get db username)]
    (when (check-bcrypt password (:password user))
      (dissoc user :password))))


(def app
  "Two workflows: one to handle login via a form POST,
   the second to handle authentication and authorization
   via session cookies."
  (let [form-workflow (form-login-workflow
                        {:authenticate authenticate-login
                         :login-uri "/login"})
        cookie-workflow (session-workflow
                          {:unauthenticated unauthenticated-session})]
    (-> routes
        (wrap-secure-secured)
        (wrap-auth cookie-workflow)
        (wrap-authentication form-workflow)
        (wrap-session)
        (api))))
