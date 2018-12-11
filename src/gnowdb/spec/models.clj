(ns gnowdb.spec.models
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.rcs :as rcs]
            )
  )


(defn- Model
  "Applies relationships to GDB_Node class and adds all dependencies required for Workspaces"
  []

   "It will be first nodetype (class) from which all nodetypes/GSystemType will inherit"
   (gneo/createClass :className "G_Node" :classType "NODE" :isAbstract? true :subClassOf ["GDB_Node"] :properties {} :execute? true)


   "Creation of MetaType which inherit Node"
   (gneo/createClass :className "MetaType" :classType "NODE" :isAbstract? true :subClassOf ["G_Node"] :properties {}  :execute? true)

   "Creation of nodetypes which inherit Metatype"
   (gneo/createClass :className "GAPP" :classType "NODE" :isAbstract? false :subClassOf ["MetaType"] :properties {} :execute? true)
   (gneo/createClass :className "factory_types" :classType "NODE" :isAbstract? false :subClassOf ["MetaType"] :properties {} :execute? true)
   (gneo/createClass :className "Mapping_relations" :classType "NODE" :isAbstract? false :subClassOf ["MetaType"] :properties {} :execute? true) 
   (gneo/createClass :className "Triadic" :classType "NODE" :isAbstract? false :subClassOf ["MetaType"] :properties {} :execute? true)
   (gneo/createClass :className "Binary" :classType "NODE" :isAbstract? false :subClassOf ["MetaType"] :properties {} :execute? true)


   "Creation of nodetypes called GSystemType"
   (gneo/createClass :className "GSystemType" :classType "NODE" :isAbstract? true :subClassOf ["G_Node"] :properties {} :execute? true)
 
   "Creation of GSystemTypes/nodetypes"
   (gneo/createClass :className "Page" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "File" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Group" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Course" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Quiz" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Image" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Forum" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Module" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Task" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Topic" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "E-Library" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Event" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "E-Book" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Reply" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Author" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Shelf" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "QuizItem" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Theme" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "theme_item" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Info page" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Blog page" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Wiki page" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "BaseCourseGroup" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Organization" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Announced Course" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Jhapp" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "Jsmol" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "base_unit" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "lesson" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "activity" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "announced_unit" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)
   (gneo/createClass :className "interactive_page" :classType "NODE" :isAbstract? false :subClassOf ["GSystemType"] :properties {} :execute? true)


  )


(defn init
  "Initializer function for all the default classes and instances."
  []
  (Model)
  )
