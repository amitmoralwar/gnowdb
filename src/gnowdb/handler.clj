(ns gnowdb.handler
  (:require [compojure.core :refer [defroutes routes]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.json :refer [wrap-json-params wrap-json-response wrap-json-body]]
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
            [gnowdb.users :as users :refer (users)]            
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))

(defn init []
  (println "liberator-service is starting"))

(defn destroy []
  (println "liberator-service is shutting down"))

(defroutes app-routes
  ;; gneo-auto-routes
  gnowdb-auto-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> 
   (handler/api app-routes)
   ;; (routes gneo-routes gneo-auto-routes workspaces-routes files-routes login-routes app-routes)  
   ;; (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn @users)
   ;;                       :workflows [(workflows/interactive-form)]
   ;;                       :allow-anon? true
   ;;                       :login-uri "/login"
   ;;                       :default-landing-uri "/api"})  
   (wrap-params)
   (wrap-keyword-params)
   (wrap-nested-params)
   (wrap-json-body)
   (wrap-json-response)
   (wrap-json-params)
   (wrap-session)    
   ;; (handler/site)
   ))
