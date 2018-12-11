(ns gnowdb.spec.nodes
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.rcs :as rcs]
            [gnowdb.spec.models :as models]
            [clojure.java.shell :refer [sh]]
            [gnowdb.spec.workspaces :as workspaces]
            [pantomime.mime :refer [mime-type-of]]
            [pantomime.extract :as extract]
            [clojure.java.io :as io]          
            )
  )


(defn- Node
  []

 
   (gneo/createAttributeType :_name "_type" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "_type" :className "G_NODE")
   (gneo/createAttributeType :_name "namee" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "namee" :className "G_NODE")
   (gneo/createAttributeType :_name "altname" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "altname" :className "G_NODE")
   (gneo/createAttributeType :_name "plural" :_datatype "java.lang.String")

   "This RT contains GSystemType 'id' for example if it's page then member_of contains 'id' of page"
   (gneo/createClass :className "member_of" :classType "RELATION" :isAbstract? false :execute? true)
   ; (gneo/addRelApplicableType :className "member_of" :applicationType "Source" :applicableClassName "G_NODE")
   ; (gneo/addRelApplicableType :className "member_of" :applicationType "Target" :applicableClassName "Page")
   ; (gneo/addRelApplicableType :className "member_of" :applicationType "Target" :applicableClassName "File")
   ; (gneo/addRelApplicableType :className "member_of" :applicationType "Target" :applicableClassName "Group")
   ; (gneo/addRelApplicableType :className "member_of" :applicationType "Target" :applicableClassName "Course")

   (gneo/addClassAT :_atname "plural" :className "G_NODE")
   (gneo/createAttributeType :_name "language" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "language" :className "G_NODE") 
   (gneo/createClass :className "type_of" :classType "RELATION" :isAbstract? false  :properties {} :execute? true)
   (gneo/createAttributeType :_name "access_policy" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "access_policy" :className "G_NODE")
   (gneo/addATVR :_atname "access_policy" :fnName "GDB_Enum" :constraintValue ["Public", "Private", "Anonymous"])
;   (gneo/addClassNC :className "G_NODE" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "access_policy")


   ; (gneo/addClassNC :className "G_NODE" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "access_policy")
   (gneo/createClass :className "created_at" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createClass :className "created_by" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createClass :className "contributor" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createAttributeType :_name "content" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "content" :className "G_NODE")
   (gneo/createAttributeType :_name "content_org" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "content_org" :className "G_NODE")
   (gneo/createAttributeType :_name "location" :_datatype "java.util.ArrayList")
   (gneo/addClassAT :_atname "location" :className "G_NODE")
   (gneo/createClass :className "group_set" :classType "RELATION" :isAbstract? false :execute? true)
   (gneo/createClass :className "collection_set" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createClass :className "tags" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createAttributeType :_name "url" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "url" :className "G_NODE")
   (gneo/createAttributeType :_name "comment_enabled" :_datatype "java.lang.Boolean")
   (gneo/addClassAT :_atname "comment_enabled" :className "G_NODE")
   (gneo/createAttributeType :_name "login_required" :_datatype "java.lang.Boolean")
   (gneo/addClassAT :_atname "login_required" :className "G_NODE")
   (gneo/createAttributeType :_name "status" :_datatype "java.lang.String")
   (gneo/addClassAT :_atname "status" :className "G_NODE")
   (gneo/createClass :className "author_set" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createClass :className "group_set" :classType "RELATION"  :isAbstract? false :execute? true)
   (gneo/createClass :className "group_admin" :classType "RELATION"  :isAbstract? false :execute? true)
;   (gneo/createAttributeType :_name "rating" :)  //is it stored as a relationship or attribute type_of
   (gneo/createAttributeType :_name "snapshot" :_datatype "java.util.ArrayList")
   (gneo/addClassAT :_atname "snapshot" :className "G_NODE")

  )


(defn init
  "Initializer function for all the default classes and instances."
  []
  (Node)
  )