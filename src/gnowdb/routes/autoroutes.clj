(ns gnowdb.routes.autoroutes
  (:gen-class)
  (:require [compojure.core :refer :all]
            [cemerick.friend :refer [authorize]]
            [gnowdb.neo4j.gneo :refer [prepMapAsArg]]
            [gnowdb.core :refer [debug-exceptions]]))

(defroutes gnowdb-auto-routes
  (context "/api" []
           (POST "/:ns/:restapifunc" request
                 (authorize #{:admin} (try (let [restfunc (var-get (ns-resolve (symbol (str "gnowdb.neo4j." (:ns (:params request))))
                                                          (symbol (:restapifunc (:params request)))))
                            json-params (into {} (for [[k v] (:json-params request)] [(keyword k) v]))] ;; Converting the first level of string keys to keywords for passing to gneo functions https://stackoverflow.com/a/9406251/6767262
                        {:body {:response (apply restfunc (prepMapAsArg json-params))
                                :gnowdb-errors? false}})
                      (catch Throwable e {:body {:response {:exceptionMessge (if debug-exceptions (.getMessage e) (str "Errors Occured. Exception hidden, debug-exceptions is off"))
                                                            :exception (if debug-exceptions (Throwable->map e) nil)}
                                                 :gnowdb-errors? true}
                                          :status 500}))))))
