(ns shepherd-examples.session
  (:require [shepherd.ring.workflow.session :refer [session-workflow
                                                    parse-identity]]
            [shepherd.ring.middleware :refer [wrap-auth]]
            [shepherd.authorization :refer [throw-unauthorized]]
            [shepherd.password :refer [bcrypt check-bcrypt]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :refer [not-found]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.session :refer [wrap-session]]
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
    (if user
      (if (check-bcrypt password (:password user))
        (-> (redirect "/")
            (assoc :session new-session))
        (redirect "/login"))
      (redirect "/login"))))


(defn logout-user
  []

  (-> (redirect "/")
      (assoc :session {})))


;;  Routes and App


(defroutes routes
  (GET "/" [] (home-view))
  (GET "/login" [] (login-view))
  (POST "/login" request (attempt-login request))
  (GET "/logout" [] (logout-user))
  (GET "/secured" [] (secured-view))
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


(defn unauthenticated
  "Function to be called if request has no identity and is
   not authorized."
  [request]

  (redirect "/login"))

(defn unauthorized
  "Function to be called if request is not authorized."
  [request identity]

  {:status 403
   :body "Permission denied."})


(def app
  (let [workflow (session-workflow
                  {:unauthenticated unauthenticated
                   :unauthoriezed unauthorized})]
    (-> routes
        (wrap-secure-secured)
        (wrap-auth workflow)
        (wrap-session)
        (api))))
