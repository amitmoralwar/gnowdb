(ns gnowdb.routes.autoroutes
  (:require [compojure.core :refer :all]
            [gnowdb.neo4j.gneo :refer [prepMapAsArg]]))

(defroutes gnowdb-auto-routes
  (context "/api" []
           (POST "/:ns/:restapifunc" request
                 (let [restfunc (var-get (ns-resolve (symbol (str "gnowdb.neo4j." (:ns (:params request))))
                                                     (symbol (:restapifunc (:params request)))))
                       json-params (into {} (for [[k v] (:json-params request)] [(keyword k) v]))] ;; Converting the first level of string keys to keywords for passing to gneo functions https://stackoverflow.com/a/9406251/6767262
                   {:body {:response (apply restfunc (prepMapAsArg json-params))}}))))
