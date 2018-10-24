(ns gnowdb.routes.gneoauto
  (:require [compojure.core :refer :all]
            [gnowdb.neo4j.gneo :refer [prepMapAsArg]]))

(defroutes gneo-auto-routes
  (context "/api/gneo" []
           (POST "/:restapifunc" request
                 (let [restfunc (var-get (ns-resolve 'gnowdb.neo4j.gneo (symbol (:restapifunc (:params request)))))
                              json-params (into {} (for [[k v] (:json-params request)] [(keyword k) v]))] ;; Converting the first level of string keys to keywords for passing to gneo functions https://stackoverflow.com/a/9406251/6767262
                          (apply restfunc (prepMapAsArg json-params))))))
