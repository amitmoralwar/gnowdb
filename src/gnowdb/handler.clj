(ns gnowdb.handler
  (:gen-class)
  (:require [compojure.core :refer [defroutes routes POST ANY]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response wrap-json-body]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace wrap-stacktrace-log]]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.session :refer [wrap-session]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            ;; [gnowdb.routes.gneo :refer [gneo-routes]]
            [gnowdb.core :refer :all] ;; So that neo4j driver connection is made
            [gnowdb.routes.autoroutes :refer [gnowdb-auto-routes]]
            ;; [gnowdb.routes.gneoauto :refer [gneo-auto-routes]]
            ;; [gnowdb.routes.workspaces :refer [workspaces-routes]]
            ;; [gnowdb.routes.files :refer [files-routes]]
            ;; [gnowdb.routes.login :refer [login-routes]]
            ;; [gnowdb.users :as users :refer (users)]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))

(def users (let [plainUsers (let [defaultUsers {"admin" {:username "admin"
                                                         :password "admin"
                                                         :roles #{:admin}}}]
                              (if (not (.exists (clojure.java.io/file "src/gnowdb/restconf.clj")))
                                (do (spit "src/gnowdb/restconf.clj" defaultUsers)
                                    defaultUsers)
                                (merge defaultUsers (read-string (slurp "src/gnowdb/restconf.clj")))))]
             (reduce (fn [m user] (update-in m [user :password] creds/hash-bcrypt)) plainUsers (keys plainUsers))))

(defn- init []
  (println "Starting REST API"))

(defn- destroy []
  (println "REST API shutting down"))

(defroutes app-routes
  ;; gneo-auto-routes
  gnowdb-auto-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> 
   (handler/api app-routes)
   ;; (routes gneo-routes gneo-auto-routes workspaces-routes files-routes login-routes app-routes)  
   (friend/authenticate {:workflows [(workflows/http-basic
                                      :credential-fn #(creds/bcrypt-credential-fn users %)
                                      :realm "gnowdb")]
                         :allow-anon? true
                         :unauthenticated-handler #(workflows/http-basic-deny "gnowdb" %)
                         })
   (wrap-params)
   (wrap-keyword-params)
   (wrap-nested-params)
   (wrap-json-body)
   (wrap-json-response)
   (wrap-json-params)
   (wrap-session)
   (wrap-stacktrace-log)
   ;; (handler/site)
   ))
