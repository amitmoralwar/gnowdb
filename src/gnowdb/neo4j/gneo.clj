(ns gnowdb.neo4j.gneo
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [gnowdb.neo4j.gdriver :as gdriver]
            [gnowdb.neo4j.gcust :as gcust]
            [gnowdb.neo4j.queryAggregator :as queryAggregator]
            )
  (:use [gnowdb.neo4j.gqb]))

(defn _keyss
  [map]
  (if (empty? map)
    []
    (clojure.core/keys map)))

(defn- initmap [m] (if (nil? m)  {} m))

(defn getUUIDEnabled
  [details]
  (def ^{:private true} uuidEnabled
    (details :uuidEnabled)
    )
  )

(defn generateUUID
  "No arguments"
  []
  (str (java.util.UUID/randomUUID))
  )

(defn getAllLabels
  "Get all the Labels from the graph, parsed.
  No arguments"
  []
  (map #(% "LABELS(n)") (((gdriver/runQuery {:query "MATCH (n) RETURN DISTINCT LABELS(n)" :parameters {}}) :results) 0)
       )
  )

(defn getAllNodes
  "Returns a lazy sequence of labels and properties of all nodes in the graph
  No arguments"
  []
  (map #(% "n") (((gdriver/runQuery {:query "MATCH (n) RETURN n" :parameters {}}) :results) 0))
  )

(defn createNewNode
  "Create a new node in the graph. Without any relationships.
  Node properties should be a clojure map.
  Map keys will be used as neo4j node keys.
  Map keys should be Strings only.
  Map values must be neo4j compatible Objects
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false  
  Mandatory arguments:
  :labels Should not be empty and must be a collection of string labels"
  [& {:keys [labels
             parameters
             execute?
             unique?
             uuid?
             queryAggregator]
      :or {execute? true
           unique? false
           uuid? uuidEnabled
           parameters {}
           queryAggregator nil}
      :as keyArgs
      }
   ]
  {:pre [(coll? labels)
         (not (empty? labels))]}
  (let [queryType 
        (if unique?
          "MERGE"
          "CREATE"
          )
        mergedParams (if uuid? (merge parameters {"UUID" (generateUUID)}) parameters)
        builtQuery  	{:query (str queryType " (node"(createLabelString :labels labels)
                                     (createParameterPropertyString
                                      mergedParams) " )"
                                     (createReturnString ["node" "UUID" "UUID"]))
                         :parameters mergedParams
                         :doRCS? true
                         :rcs-vars ["UUID"]
                         :labels labels}
        ]
    (if execute?
      ((gdriver/runQuery builtQuery) :summary)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

;; (defn createNodes
;;   "Create Multiple Nodes in One Query"
;;   [])

(defn deleteRelation
  "Delete a relation between two nodes matched with their properties (input as clojure map) with it's own properties
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false  
  No mandatory arguments, when used with no parameters, it will return a query to delete all relations in the neo4j db"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             execute?
             queryAggregator]
      :or {fromNodeLabels []
           toNodeLabels []
           relationshipType ""
           execute? false
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           queryAggregator nil}}]
  {:pre [
         (every? coll? [fromNodeLabels toNodeLabels])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [fromNodeLabel (createLabelString :labels fromNodeLabels)
        toNodeLabel (createLabelString :labels toNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel"relationshipTypewb" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " )  DELETE rel"
              (createReturnString ["node2" "UUID" "UUID"]))
         :doRCS? true
         :rcs-vars ["UUID"]
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      ((gdriver/runQuery builtQuery) :summary)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn editRelation
  "Edit Parameters of a relation
  Mandatory arguments:
  :newRelelationshipParamters should be a non-empty clojure map
  Not specifying :fromNodeLabels, :toNodeLabels, and the other arguments,
  will affect all relations in the graphdb so use wisely
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             newRelationshipParameters
             execute?
             queryAggregator]
      :or {execute? false
           fromNodeLabels []
           toNodeLabels []
           relationshipType ""
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           queryAggregator nil}}]
  {:pre [
         (every? coll? [fromNodeLabels toNodeLabels])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])
         (map? newRelationshipParameters)
         (not (empty? newRelationshipParameters))]}
  (let [toNodeLabel (createLabelString :labels toNodeLabels)
        fromNodeLabel (createLabelString :labels fromNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          "RE" newRelationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel"relationshipTypewb" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " ) "(createEditString :varName "rel"
                                     :editPropertyList (keys (addStringToMapKeys newRelationshipParameters "RE"))
                                     :characteristicString "RE")
              (createReturnString ["node2" "UUID" "UUID"])
              )
         :doRCS? true
         :rcs-vars ["UUID"]
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn editRelationPropList
  "Edit Parameter of a relation that is a List
  :propName should be string, representing the propertyName.(Mandatory)
  :editType should be one of APPEND,DELETE,REPLACE.(Mandatory)
  :editVal should represent value for APPEND/DELETE/REPLACE.(Mandatory)
  :replaceVal should be intended value, if :editVal is REPLACE
  As usual, not specifying :fromNodeLabels, :toNodeLabels, :fromNodeParameters etc,
  will affect all nodes/relations.
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             propName
             editType
             editVal
             replaceVal
             execute?
             queryAggregator]
      :or {execute? true
           toNodeLabels []
           fromNodeLabels []
           relationshipType ""
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           queryAggregator nil}}]
  {:pre [(every? coll? [fromNodeLabels toNodeLabels])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [toNodeLabel (createLabelString :labels toNodeLabels)
        fromNodeLabel (createLabelString :labels fromNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel"relationshipTypewb" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " ) "(createPropListEditString :varName "rel"
                                             :propName propName
                                             :editType editType
                                             :editVal "ATT"
                                             :replaceVal "att")
              (createReturnString ["node" "UUID" "UUID"]))         
         :doRCS? true
         :rcs-vars ["UUID"]
         :parameters (merge {"ATT" editVal
                             "att" replaceVal}
                            (combinedProperties :combinedPropertyMap)
                            )
         }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn createRelation
  "Relate two nodes matched with their properties (input as clojure map) with it's own properties
  No mandatory Arguments, but when using with no arguments it will create a junk relation between ALL nodes
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             execute?
             unique?
             queryAggregator]
      :or {execute? true
           unique? false
           toNodeLabels []
           fromNodeLabels []
           relationshipType ""
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           queryAggregator nil}}]
  {:pre [(every? coll? [fromNodeLabels toNodeLabels])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [toNodeLabel (createLabelString :labels toNodeLabels)
        fromNodeLabel (createLabelString :labels fromNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        unique
        (if unique?
          "UNIQUE"
          ""
          )
        builtQuery
        {:query
         (str "MATCH (node1" fromNodeLabel " "
              ((combinedProperties :propertyStringMap) "1")
              " ) , (node2" toNodeLabel " "
              ((combinedProperties :propertyStringMap) "2")
              " ) CREATE " unique " (node1)-[" relationshipTypewb " "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2)"
              (createReturnString ["node2" "UUID" "UUID"]))
         :doRCS? true
         :rcs-vars ["UUID"]
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn mergeRelation
  "Create, if not exists, a relation betrween two nodes matched with their properties (input as clojure map) with it's own properties
  No mandatory arguments. But use wisely
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             execute?
             queryAggregator]
      :or {execute? true
           toNodeLabels []
           fromNodeLabels []
           relationshipType ""
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           queryAggregator nil}}]
  {:pre [(every? coll? [fromNodeLabels toNodeLabels])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [toNodeLabel (createLabelString :labels toNodeLabels)
        fromNodeLabel (createLabelString :labels fromNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        builtQuery {:query
                    (str "MATCH (node1" fromNodeLabel " "
                         ((combinedProperties :propertyStringMap) "1")
                         " ) , (node2" toNodeLabel " "
                         ((combinedProperties :propertyStringMap) "2")
                         " ) MERGE  (node1)-[" relationshipTypewb " "
                         ((combinedProperties :propertyStringMap) "R")
                         " ]->(node2)"
                         (createReturnString ["node2" "UUID" "UUID"]))
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn deleteNodes
  "Delete node(s) matched using property map
  No mandatory Arguments, but if you execute without arguments, it will try
  to delete all nodes!!
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             execute?
             queryAggregator]
      :or {labels []
           execute? false
           parameters {}
           queryAggregator nil}
      }
   ]
  (let [builtQuery {:query (str "MATCH (node"(createLabelString :labels labels)" "
                                (createParameterPropertyString parameters)
                                " ) WITH node, node.UUID AS UUID DELETE node"
                                " RETURN UUID")
                    :parameters parameters
                    :rcs-vars ["UUID"]
                    :rcs-bkp? true}
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn deleteDetachNodes
  "Delete node(s) matched using property map and detach (remove relationships)
  No mandatory arguments, but use wisely
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             execute?
             queryAggregator]
      :or {labels []
           execute? false
           parameters {}
           queryAggregator nil}
      }
   ]
  (let [builtQueries [(deleteRelation :fromNodeLabels labels
                                      :fromNodeParameters parameters
                                      :execute? false)
                      (deleteRelation :toNodeLabels labels
                                      :toNodeParameters parameters
                                      :execute? false)
                      (deleteNodes :labels labels
                                   :parameters parameters
                                   :execute? false)]
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn removeNodeProperties
  "Remove Properties of Node(s).
  :propList should be a list of properties to be deleted.
  No mandatory arguments, but will try to fetch all node UUID when no
  arguments are given
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             propList
             execute?
             queryAggregator]
      :or {labels []
           propList []
           execute? true
           parameters {}
           queryAggregator nil}
      }
   ]
  {:pre [(map? parameters)
         (coll? propList)
         (every? string? propList)]}
  (let [builtQuery {:query (str "MATCH (node"(createLabelString :labels labels)" "
                                (createParameterPropertyString parameters)
                                " ) "(createRemString :varName "node"
                                                      :remPropertyList propList)
                                (createReturnString ["node" "UUID" "UUID"]))
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters parameters
                    }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn editNodeProperties
  "Edit Properties of Node(s)
  No mandatory arguments, but will try to fetch all node UUIDs when no
  arguments are given
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             changeMap
             execute?
             queryAggregator]
      :or {labels []
           changeMap {}
           execute? true
           parameters {}
           queryAggregator nil}
      }
   ]
  {:pre [(map? parameters)
         (map? changeMap)]}
  (let [mPM (addStringToMapKeys parameters "M")
        tPME (addStringToMapKeys changeMap "E")
        builtQuery {:query (str "MATCH (node1"(createLabelString :labels labels)" "
                                (createParameterPropertyString mPM "M")
                                " ) "(createEditString :varName "node1"
                                                       :editPropertyList (keys tPME)
                                                       :characteristicString "E"
                                                       )
                                (createReturnString ["node1" "UUID" "UUID"]))
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters (merge mPM tPME)
                    :labels labels
                    }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn renameNodeProperties
  "Rename node(s) properties.
  :labels should be a collection of string labels
  :parameters should be a map to match a node or nodes with
  :renameMap should be a map eg. {'oldParameterName' 'NewParameterName'} (Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             renameMap
             execute?
             queryAggregator]
      :or {labels []
           execute? true
           parameters {}
           queryAggregator nil}
      }
   ]
  {:pre [(map? parameters)
         (map renameMap)]}
  (let [builtQuery
        {:query (str "MATCH (node"(createLabelString :labels labels)" "
                     (createParameterPropertyString parameters) " ) "
                     (createRenameString :varName "node"
                                         :renameMap renameMap)
                     (createReturnString ["node" "UUID" "UUID"]))
         :doRCS? true
         :rcs-vars ["UUID"]
         :parameters {}}
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn editNodePropList
  "Edits a property of a node with a list as a value.
  :labels should be collection.
  :parameters should be a map.
  :propName should be string, representing the propertyName.(Mandatory)
  :editType should be one of APPEND,DELETE,REPLACE.(Mandatory)
  :editVal should represent value for APPEND/DELETE/REPLACE.(Mandatory if not deleting)
  :replaceVal should be intended value, if :editVal is REPLACE
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             parameters
             propName
             editType
             editVal
             replaceVal
             execute?
             queryAggregator]
      :or {labels []
           replaceVal nil
           parameters {}
           execute? true
           queryAggregator nil}
      }
   ]
  {:pre [(map? parameters)]}
  (let [combinedPropertyMap (combinePropertyMap {"NP" parameters})
        builtQuery {:query (str "MATCH (node"(createLabelString :labels labels)" "
                                ((combinedPropertyMap :propertyStringMap) "NP")
                                ") "(createPropListEditString :varName "node"
                                                              :propName propName
                                                              :editType editType
                                                              :editVal "ATT"
                                                              :replaceVal "att")
                                (createReturnString ["node" "UUID" "UUID"]))
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters (merge {"ATT" editVal "att" replaceVal}
                                       (combinedPropertyMap :combinedPropertyMap)
                                       )
                    }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn removeLabels
  "Removes labels from a node
  :remLabelList should be a list of strings (Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             properties
             remLabelList
             execute?
             queryAggregator]
      :or {labels []
           properties {}
           execute? true
           queryAggregator nil}}]
  {:pre [(map? properties)
         (coll? remLabelList)
         (every? string? remLabelList)
         (not (empty? remLabelList))]}
  (let [builtQuery {:query (str "MATCH (obj"(createLabelString :labels labels)" "(createParameterPropertyString properties)") "
                                (clojure.string/join " " (map #(str "REMOVE obj:"(backtick %)) remLabelList))
                                (createReturnString ["obj" "UUID" "UUID"]))
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters properties}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn renameLabels
  "Renames label(s) of a node/relation.
  :objectType should be NODE or RELATION.(Mandatory)
  :replaceLabelMap should be a map of strings, with keys as existing labels and the values as newLabels.(Mandatory)
  if a label with a particular key doesnt exist, the new label will be added.
  if :objectType is RELATION, replaceLabelmap can only have one string
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [labels
             properties
             objectType
             replaceLabelMap
             execute?
             queryAggregator]
      :or {labels []
           properties {}
           objectType "NODE"
           execute? true
           queryAggregator nil}}]
  {:pre [(map? properties)
         (map? replaceLabelMap)
         (every? string? (vals replaceLabelMap))
         (every? string? (keys replaceLabelMap))
         (not (empty? replaceLabelMap))
         (or (= "NODE" objectType)
             (and (= "RELATION" objectType)
                  (= 1 (count labels))
                  (= 1 (count replaceLabelMap))
                  (not (nil? (replaceLabelMap (first labels))))
                  )
             )
         ]
   }
  (let [builtQuery {:query (case objectType
                             "NODE" (str "MATCH (obj"(createLabelString :labels labels)" "(createParameterPropertyString properties)") "
                                         (clojure.string/join " " (map #(str "REMOVE obj:"(% 0)" "
                                                                             "SET obj:"(% 1)) replaceLabelMap))
                                         (createReturnString ["obj" "UUID" "UUID"]))
                             "RELATION" (str "MATCH (n1)-[rel"(createLabelString :labels labels)"]->(obj)"
                                             " MERGE (n1)-[rel2"(createLabelString :labels [(replaceLabelMap (first labels))])"]-(obj)"
                                             " SET rel2=rel"
                                             " WITH rel, obj"
                                             " DELETE rel"
                                             (createReturnString ["obj" "UUID" "UUID"]))
                             )
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :parameters properties}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn revertNode
  "Revert Properties/Labels of a node to an older version.
  No mandatory arguments, but when no arguments are given, it will
  try to fetch all node UUID
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [matchLabels
             newLabels
             matchProperties
             newProperties
             execute?
             queryAggregator]
      :or {matchLabels []
           newLabels []
           matchProperties {}
           newProperties {}
           execute? true
           queryAggregator nil}}]
  {:pre [(coll? matchLabels)
         (coll? newLabels)
         (map? matchProperties)
         (map? newProperties)]}
  (let [remLabels (clojure.set/difference (into #{} matchLabels)
                                          (into #{} newLabels))
        addLabels (clojure.set/difference (into #{} newLabels)
                                          (into #{} matchLabels))
        remProperties (clojure.set/difference (into #{} (keys matchProperties))
                                              (into #{} (keys newProperties)))
        combinedPropertyMap (combinePropertyMap {"M" matchProperties
                                                 "E" newProperties})
        builtQuery {:query (str "MATCH (n"(createLabelString :labels matchLabels)
                                " "((combinedPropertyMap :propertyStringMap) "M")")"
                                " "(createRemString :varName "n"
                                                    :remPropertyList remProperties)
                                (if (empty? remLabels)
                                  " "
                                  (if (empty? remProperties)
                                    (str " REMOVE n"(createLabelString :labels remLabels))
                                    (str " ,n"(createLabelString :labels remLabels))))
                                " "(if (empty? newProperties)
                                     ""
                                     (createEditString :varName "n"
                                                       :editPropertyList (keys (addStringToMapKeys newProperties "E"))
                                                       :characteristicString "E"))
                                (if (empty? addLabels)
                                  " "
                                  (if (empty? newProperties)
                                    (str " SET n"(createLabelString :labels addLabels))
                                    (str " ,n"(createLabelString :labels addLabels))))
                                " "(createReturnString ["n" "UUID" "UUID"]))
                    :parameters (combinedPropertyMap :combinedPropertyMap)
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :labels newLabels}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn revertRelation
  "Revert properties/type of a relation
  Mandatory arguments:
  :relationshipType
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             toNodeLabels
             toNodeParameters
             relationshipType
             matchRelationshipParameters
             newRelationshipParameters
             execute?
             queryAggregator]
      :or {fromNodeLabels []
           fromNodeParameters {}
           toNodeLabels []
           toNodeParameters {}
           matchRelationshipParameters {}
           newRelationshipParameters {}
           execute? false
           queryAggregator nil}}]
  {:pre [(string? relationshipType)
         (coll? fromNodeLabels)
         (coll? toNodeLabels)
         (map? fromNodeParameters)
         (map? toNodeParameters)
         (map? matchRelationshipParameters)
         (map? newRelationshipParameters)]}
  (let [remProperties (clojure.set/difference (into #{} (keys matchRelationshipParameters))
                                              (into #{} (keys newRelationshipParameters)))
        combinedPropertyMap (combinePropertyMap {"N1" toNodeParameters
                                                 "N2" fromNodeParameters
                                                 "RM" matchRelationshipParameters
                                                 "RE" newRelationshipParameters})
        builtQuery {:query (str "MATCH (n1"(createLabelString :labels toNodeLabels)
                                " "((combinedPropertyMap :propertyStringMap) "N1")")"
                                "<-[rel"(createLabelString :labels [relationshipType])
                                " "((combinedPropertyMap :propertyStringMap) "RM")"]"
                                "-(n2"(createLabelString :labels fromNodeLabels)
                                " "((combinedPropertyMap :propertyStringMap) "N2")")"
                                " "(if (empty? remProperties)
                                     ""
                                     (createRemString :varName "rel"
                                                      :remPropertyList remProperties))
                                " "(createEditString :varName "rel"
                                                     :editPropertyList (keys (addStringToMapKeys newRelationshipParameters
                                                                                                 "RE"))
                                                     :characteristicString "RE")
                                " "(createReturnString ["n1" "UUID" "UUID"]))
                    :parameters (combinedPropertyMap :combinedPropertyMap)
                    :doRCS? true
                    :rcs-vars ["UUID"]
                    :labels toNodeLabels}]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn getNodes
  "Get Node(s) matched by label and propertyMap
  No mandatory arguments"
  [& {:keys [labels
             parameters
             count?
             execute?]
      :or {labels []
           parameters {}
           count? false}}]
  {:pre [(map? parameters)]}
  (map #(% (if count?
             "count(node)"
             "node")) (((gdriver/runQuery {:query (str "MATCH (node"(createLabelString :labels labels)" "
                                                       (createParameterPropertyString parameters)
                                                       ") RETURN " (if count?
                                                                     "count(node)"
                                                                     "node"))
                                           :parameters parameters})
                        :results) 0)
       )
  )

(defn getRelations
  "Get relations matched by inNode/outNode/type and properties
  No mandatory arguments"
  [& {:keys [fromNodeLabels
             fromNodeParameters
             relationshipType
             relationshipParameters
             toNodeLabels
             toNodeParameters
             count?
             execute?
             nodeInfo?]
      :or {execute? true
           toNodeLabels []
           fromNodeLabels []
           toNodeParameters {}
           fromNodeParameters {}
           relationshipParameters {}
           relationshipType ""
           nodeInfo? false}}]
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        fromNodeLabel (createLabelString :labels fromNodeLabels)
        toNodeLabel (createLabelString :labels toNodeLabels)
        relationshipTypewb (createLabelString :labels [relationshipType])
        builtQuery
        {:query
         (if nodeInfo?
           (str "MATCH path=(n" fromNodeLabel " "
                ((combinedProperties :propertyStringMap) "1")
                ")-[p" relationshipTypewb " "
                ((combinedProperties :propertyStringMap) "R")
                "]->(m" toNodeLabel " "
                ((combinedProperties :propertyStringMap) "2")
                ") RETURN " (if count?
                              "count(path)"
                              "path"))
           (str "MATCH (n" fromNodeLabel " "
                ((combinedProperties :propertyStringMap) "1")
                ")-[p" relationshipTypewb " "
                ((combinedProperties :propertyStringMap) "R")
                "]->(m" toNodeLabel " "
                ((combinedProperties :propertyStringMap) "2")
                ") RETURN " (if count?
                              "count(p)"
                              "p"))
           )
         :parameters
         (combinedProperties :combinedPropertyMap)
         }
        ]
    (if execute?
      (if nodeInfo?
        (map #(first ((% (if count?
                           "count(path)"
                           "path")) :segments))
             (first ((gdriver/runQuery builtQuery) :results))) 
        (map #(% (if count?
                   "count(p)"
                   "p"))
             (first ((gdriver/runQuery builtQuery) :results)))
        )
      builtQuery
      )
    )
  )

(defn getNeighborhood
  "Get the neighborhood of a particular node
  No mandatory arguments.
  WARNING - DEPRECATED, not used. Use gdriver/getNBH instead"
  [& {:keys [labels
             parameters]
      :or {parameters {}}
      }
   ]
  (let [nodeseq (getNodes :labels labels
                          :parameters parameters)
        ]
    (if (not= (count nodeseq) 1)
      "Error"
      (let [nodeLabels ((first nodeseq) :labels)
            nodeParameters ((first nodeseq) :properties)
            ]
        {:labels nodeLabels
         :properties nodeParameters
         :outNodes (map #(select-keys % [:labels
                                         :properties
                                         :toNode])
                        (getRelations :fromNodeLabels nodeLabels
                                      :fromNodeParameters nodeParameters)
                        )
         :inNodes (map #(select-keys % [:labels
                                        :properties
                                        :fromNode])
                       (getRelations :toNodeLabels nodeLabels
                                     :toNodeParameters nodeParameters)
                       )
         }
        )
      )
    )
  )

(defn prepMapAsArg
  "Converts a map so that it can be used as keyArgs"
  [keyMap]
  (reduce #(concat %1 %2) {} keyMap))

(defn reduceQueryColl
  "Reduce collections/sub-collections of queries (({:query '...' :parameters {}})...()...()...) to a single collection of queries ({:query '...' :parameters {}} {...} {...})"
  [queryCollection]
  (reduce
   #(if
        (some map? %2)
      (concat %1 %2)
      (if (map? %2)
        (concat %1 [%2])
        (reduceQueryColl %1))
      )
   []
   queryCollection
   )
  )

(defn manageConstraints
  "Manage unique constraints or existance constraints.
  :label should be a string.(Mandatory)
  :CD should be either of 'CREATE','DROP'(Mandatory)
  :propertyVec should be a vector of properties(string).(Mandatory)
  :constraintType should be either UNIQUE or NODEEXISTANCE or RELATIONEXISTANCE or NODEKEY.(Mandatory)
  if :constraintType is NODEKEY, :propertyVec should be a vector of vectors of properties(string).
  :execute? (boolean) whether the constraints are to be created, or just return preparedQueries
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [label
             CD
             propertyVec
             constraintType
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      }
   ]
  {:pre [
         (string? label)
         (contains? #{"CREATE" "DROP"} CD)
         (not (empty? propertyVec))
         (contains?   #{"UNIQUE"
                        "NODEEXISTANCE"
                        "RELATIONEXISTANCE"
                        "NODEKEY"} constraintType)
         (if
             (= "NODEKEY" constraintType)
           (every? #(and (coll? %)
                         (not (empty? %))
                         )
                   propertyVec)
           true
           )
         ]
   }
  (let [bklabel (backtick label)
        queryBuilder (case constraintType
                       "UNIQUE" #(str "(label:" bklabel
                                      ") ASSERT label." % " IS UNIQUE")
                       "NODEEXISTANCE" #(str "(label:" bklabel
                                             ") ASSERT exists(label." % ")")
                       "RELATIONEXISTANCE" #(str "()-[label:" bklabel
                                                 "]-() ASSERT exists(label." % ")")
                       "NODEKEY" #(str "(label:" bklabel
                                       ") ASSERT (" (clojure.string/join
                                                     ", "
                                                     (map (fn [property]
                                                            (str "label." property)
                                                            )
                                                          %)
                                                     )
                                       ") IS NODE KEY"
                                       )
                       )
        builtQueries (map #(->
                            {:query
                             (str CD " CONSTRAINT ON " (queryBuilder %))
                             :parameters {}
                             :schema-changed? true
                             :override-nochange (if (= "NODEKEY" constraintType)
                                                  true;; As neo4j returns containsUpdates false for nodekey constraints
                                                  false)
                             }
                            ) propertyVec)
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn manageUniqueConstraints
  "Create/Drop Unique Constraints on label properties.
  :label is treated as a Node Label.(Mandatory)
  :CD can be CREATE,DROP.(Mandatory)
  :propertyVec should be a vector of properties(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [label
             CD
             propertyVec
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}
   ]
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           keyArgs
           :constraintType "UNIQUE"
           ))))

(defn manageExistanceConstraints
  "Create/Drop Existance Constraints on label properties.
  :label is treated as a Node label or relation label based on value of NR.(Mandatory)
  :CD can be CREATE, DROP.(Mandatory)
  :propertyVec should be a vector of properties.(Mandatory)
  :NR should be either NODE or RELATION(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [label
             CD
             propertyVec
             NR
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}]
  {:pre [(contains? #{"CREATE" "DROP"} CD)]
   }
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           (dissoc
            keyArgs :NR)
           :constraintType (str NR "EXISTANCE")))))

(defn manageNodeKeyConstraints
  "Create/Drop Node Key Constraints on node label properties.
  :label is treated as node label.(Mandatory)
  :CD can be CREATE, DROP.(Mandatory)
  :propPropVec should be a vector of vectors of properties(string).(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [label
             CD
             propPropVec
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}]
  ;;For some reason, creating/dropping a nodekey doesn't reflect on summary.
  ;;So don't be surprised if no errors occur or no changes exist in the summary.
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           keyArgs
           :constraintType "NODEKEY"
           :propertyVec propPropVec))))

(defn createNCConstraints
  "Create Constraints that apply to nodes with label NeoConstraint"
  [& {:keys [execute?] :or {execute? true}}]
  (manageNodeKeyConstraints :label "NeoConstraint"
                            :CD "CREATE"
                            :propPropVec [["constraintType" "constraintTarget"]]
                            :execute? execute?)
  )

(defn createCATConstraints
  "Create Constraints that apply to relationships with label NeoConstraintAppliesTo"
  [& {:keys [execute?]
      :or {execute? true}}]
  (manageExistanceConstraints :label "NeoConstraintAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue"]
                              :NR "RELATION"
                              :execute? execute?)
  )

(defn createATConstraints
  "Creates Constraints that apply to nodes with label AttributeType"
  [& {:keys [execute?]
      :or
      {execute? true}}
   ]
  (manageNodeKeyConstraints :label "AttributeType"
                            :CD "CREATE"
                            :propPropVec [["_name"]]
                            :execute? execute?)
  )

(defn createCFConstraints
  "Creates Constraints that apply to nodes with label CustomFunction"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (let [builtQueries (reduceQueryColl
                      [(manageNodeKeyConstraints :label "CustomFunction"
                                                 :CD "CREATE"
                                                 :propPropVec [["fnName"]]
                                                 :execute? false)
                       (manageExistanceConstraints :label "CustomFunction"
                                                   :CD "CREATE"
                                                   :propertyVec ["fnString" "fnIntegrity"]
                                                   :NR "NODE"
                                                   :execute? false)
                       ]
                      )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)))

(defn createVRATConstraints
  "Creates Constraints that apply to relations with label ValueRestrictionAppliesTo"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (manageExistanceConstraints :label "ValueRestrictionAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue"]
                              :NR "RELATION"
                              :execute? execute?))

(defn createCCATConstraints
  "Creates Constraints that apply to relations with label CustomConstraintAppliesTo"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (manageExistanceConstraints :label "CustomConstraintAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue" "atList"]
                              :NR "RELATION"
                              :execute? execute?))

(defn createClassConstraints
  "Create Constraints that apply to nodes with label Class"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (let [builtQueries (reduceQueryColl
                      [(manageExistanceConstraints :label "Class"
                                                   :CD "CREATE"
                                                   :propertyVec ["isAbstract" "classType"]
                                                   :NR "NODE"
                                                   :execute? false)
                       (manageNodeKeyConstraints :label "Class"
                                                 :CD "CREATE"
                                                 :propPropVec [["className"]]
                                                 :execute? false)
                       ]
                      )
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)))

(defn createNeoConstraint
  "Creates a NeoConstraint Node that describes a supported neo4j constraint.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.(Mandatory)
  :constraintTarget should be either of NODE,RELATION.(Mandatory)
  If :constraintTarget is RELATION, then constraintType can only be EXISTANCE"
  [& {:keys [constraintType
             constraintTarget
             execute?]
      :or {execute? true}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "EXISTANCE" "NODEKEY"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)
         (not
          (and
           (contains? #{"UNIQUE" "NODEKEY"} constraintType)
           (= "RELATION" constraintTarget)
           )
          )
         ]
   }
  (createNewNode :labels ["NeoConstraint"]
                 :parameters {"constraintType" constraintType
                              "constraintTarget" constraintTarget}
                 :execute? execute?
                 )
  )

(defn createAllNeoConstraints
  "Creates all NeoConstraints"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (let [builtQueries
        (map #(createNeoConstraint
               :constraintType (% 0)
               :constraintTarget (% 1)
               :execute? false)
             [
              ["UNIQUE" "NODE"]
              ["NODEKEY" "NODE"]
              ["EXISTANCE" "NODE"]
              ["EXISTANCE" "RELATION"]
              ]
             )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries))
  )

(def validATDatatypes #{"java.lang.Boolean",
                        "java.lang.Byte",
                        "java.lang.Short",
                        "java.lang.Integer",
                        "java.lang.Long",
                        "java.lang.Float",
                        "java.lang.Double",
                        "java.lang.Character",
                        "java.lang.String",
                        "java.util.ArrayList"})

(def defaultDatatypeValues {"java.lang.Boolean" false
                            "java.lang.Byte" (byte 0)
                            "java.lang.Short" (short 0)
                            "java.lang.Integer" (int 0)
                            "java.lang.Long" (long 0)
                            "java.lang.Float" (float 0)
                            "java.lang.Double" (double 0)
                            "java.lang.Character" (char 0)
                            "java.lang.String" ""
                            "java.util.ArrayList" []})

(defn createCustomFunction
  "Creates a customFunction.
  :fnName should be string.(Mandatory)
  :fnString should be string that represents CustomFunction template.(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fnName
             fnString
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(gcust/stringIsCustFunction? fnString)
         (string? fnName)]}
  (createNewNode :labels ["CustomFunction"]
                 :parameters {	"fnName" fnName
                              "fnString" fnString
                              "fnIntegrity" (gcust/hashCustomFunction fnString)}
                 :execute? execute?
                 :queryAggregator queryAggregator))

(defn getCustomFunctions
  "Get CustomFunctions
  No mandatory arguments"
  [& {:keys [count?]
      :or {count? false}}]
  (getNodes :labels ["CustomFunction"]
            :parameters {}
            :count? count?))

(defn editCustomFunction
  "Edit a CustomFunction's fnName and/or fnString
  :fnName should be the desired fnName(Mandatory)
  :changeMap should be a map with keys 'fnName', 'fnString'
  with the desired values(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [fnName
             changeMap
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}]
  {:pre [(string? fnName)
         (map? changeMap)
         (not (empty? changeMap))
         (clojure.set/subset? (into #{} (keys changeMap)) #{"fnName"
                                                            "fnString"}
                              )
         (or (and (contains? changeMap "fnString")
                  (gcust/stringIsCustFunction? (changeMap "fnString")
                                               )
                  )
             (not (contains? changeMap "fnString")))
         ]
   }
  (let [mChangeMap (merge changeMap (if (contains? changeMap "fnString")
                                      {"fnIntegrity" (gcust/hashCustomFunction (changeMap "fnString"))}
                                      {})
                          )]
    (editNodeProperties :labels ["CustomFunction"]
                        :parameters {"fnName" fnName}
                        :changeMap mChangeMap
                        :execute? execute?
                        :queryAggregator queryAggregator)))

(defn reHashCustomFunctions
  "Re-Hash all CustomFunctions.
  Could take a long time depending on size of database.
  Use only when customPassword is changed
  No mandatory arguments
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}]
  (let [builtQueries (map (fn [customFunction]
                            (let [cf (into {} (customFunction :properties))]
                              (editCustomFunction :fnName (cf "fnName")
                                                  :changeMap {"fnString" (cf "fnString")}
                                                  :execute? false
                                                  )
                              )
                            )
                          (getCustomFunctions)
                          )]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn getClassAttributeTypes
  "Get all AttributeTypes 'attributed' to a class
  :className should be a string (Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(att)"
              "att")) :properties) (((gdriver/runQuery
                                      {:query (str "MATCH (class:`Class` {className:{className}})-[rel:`HasAttributeType`]->(att:`AttributeType`) RETURN "(if count?
                                                                                                                                                            "count(att)"
                                                                                                                                                            "att"))
                                       :parameters {"className" className}
                                       }
                                      ) :results) 0))
  )

(defn getClassApplicableSourceNT
  "Get all Source NodeTypes 'attributed' to a class
  :className should be the name of the class(Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(rl)"
              "rl")) :properties) (((gdriver/runQuery
                                     {:query (str "MATCH (class:`Class` {className:{className}})<-[rel:`ApplicableSourceNT`]-(rl:`Class`) RETURN "(if count?
                                                                                                                                                    "count(rl)"
                                                                                                                                                    "rl"))
                                      :parameters {"className" className}
                                      }
                                     ) :results) 0))
  )

(defn getClassApplicableTargetNT
  "Get all Target NodeTypes 'attributed' to a class
  :className should be string(Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(rl)"
              "rl")) :properties) (((gdriver/runQuery
                                     {:query (str "MATCH (class:`Class` {className:{className}})<-[rel:`ApplicableTargetNT`]-(rl:`Class`) RETURN "(if count?
                                                                                                                                                    "count(rl)"
                                                                                                                                                    "rl"))
                                      :parameters {"className" className}
                                      }
                                     ) :results) 0))
  )

(defn getClassNeoConstraints
  "Get all NeoConstraints attributed to a class
  :className should be string(Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (gdriver/runQuery
   {:query (str "MATCH (class:`Class` {className:{className}})<-[ncat:`NeoConstraintAppliesTo`]-(neo:`NeoConstraint`) RETURN " (if count?
                                                                                                                                 "count(ncat)"
                                                                                                                                 "ncat,neo"))
    :parameters {"className" className}
    }
   )
  )

(defn getClassCustomConstraints
  "Get all CustomConstraints applicable to a class
  :className should be string(Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (gdriver/runQuery
   {:query (str "MATCH (class:`Class` {className:{className}})<-[ccat:`CustomConstraintAppliesTo`]-(cf:`CustomFunction`) RETURN "(if count?
                                                                                                                                   "count(ccat)"
                                                                                                                                   "ccat,cf"))
    :parameters {"className" className}
    }
   )
  )

(defn getATValueRestrictions
  "Get all ValueRestrictions applicable to an AttributeType with _name atname
  :atName should be string(Mandatory)"
  [& {:keys [atName
             count?]
      :or {count? false}}]
  {:pre [(string? atName)]}
  (gdriver/runQuery
   {:query (str "MATCH (at:`AttributeType` {_name:{atname}})<-[vr:`ValueRestrictionAppliesTo`]-(cf:`CustomFunction`) RETURN "(if count?
                                                                                                                               "count(vr)"
                                                                                                                               "cf,vr"))
    :parameters {"atname" atName}
    }
   )
  )

(defn exemptClassNeoConstraint
  "Exempt a NeoConstraint that currently applies to a class.
  :className string(Mandatory)
  :constraintType UNIQUE,NODEKEY,EXISTANCE(Mandatory)
  :constraintTarget NODE,RELATION(Mandatory)
  :constraintValue depends upon :_constraintTarget and :_constraintType(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             constraintType
             constraintTarget
             constraintValue
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "NODEKEY" "EXISTANCE"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)]}
  (manageConstraints :label className
                     :CD "DROP"
                     :propertyVec [(if
                                       (= "NODEKEY" constraintType)
                                     (into [] constraintValue)
                                     constraintValue
                                     )
                                   ]
                     :constraintType (case constraintType
                                       ("UNIQUE" "NODEKEY") constraintType
                                       "EXISTANCE" (str constraintTarget constraintType)
                                       )
                     :execute? execute?
                     :queryAggregator queryAggregator))

(defn applyClassNeoConstraint
  "Apply a NeoConstraint that apply to a class.
  :className string(Mandatory)
  :constraintType UNIQUE,NODEKEY,EXISTANCE(Mandatory)
  :constraintTarget NODE,RELATION(Mandatory)
  :constraintValue depends upon :_constraintTarget and :_constraintType(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             constraintType
             constraintTarget
             constraintValue
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "NODEKEY" "EXISTANCE"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)]}
  (manageConstraints :label className
                     :CD "CREATE"
                     :propertyVec [(if
                                       (= "NODEKEY" constraintType)
                                     (into [] constraintValue)
                                     constraintValue
                                     )
                                   ]
                     :constraintType (case constraintType
                                       ("UNIQUE" "NODEKEY") constraintType
                                       "EXISTANCE" (str constraintTarget constraintType)
                                       )
                     :execute? execute?
                     :queryAggregator queryAggregator))

(defn exemptClassNeoConstraints
  "Exempt all NeoConstraints for a class
  :className string(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}]
  (let [builtQueries 
        (reduceQueryColl
         (map
          #(apply exemptClassNeoConstraint
                  (prepMapAsArg
                   (assoc
                    (clojure.set/rename-keys
                     (merge keyArgs
                            (into {} ((% "neo") :properties))
                            (into {} ((% "ncat") :properties))
                            )
                     {"constraintValue" :constraintValue
                      "constraintType" :constraintType
                      "constraintTarget" :constraintTarget
                      }
                     )
                    :execute? false
                    )
                   )
                  )
          (((getClassNeoConstraints :className className) :results) 0)
          )
         )
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn applyClassNeoConstraints
  "Apply all NeoConstraints for a class
  :className string (Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator with :schema? true
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}
      :as keyArgs}]
  (let [builtQueries 
        (reduceQueryColl
         (map
          #(apply applyClassNeoConstraint
                  (prepMapAsArg
                   (assoc
                    (clojure.set/rename-keys
                     (merge keyArgs
                            (into {} ((% "neo") :properties))
                            (into {} ((% "ncat") :properties))
                            )
                     {"constraintValue" :constraintValue
                      "constraintType" :constraintType
                      "constraintTarget" :constraintTarget
                      }
                     )
                    :execute? false
                    )
                   )
                  )
          (((getClassNeoConstraints :className className) :results) 0)
          )
         )
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn remRelApplicableType
  "Remove an applicable Source/Target type to a Relation Class, by removing a relation: ApplicableSourceNT/ApplicableTargetNT.
  :className should be className of relation class.(Mandatory)
  :applicationType should be either SOURCE or TARGET as string.(Mandatory)
  :applicableClassName should be a className of the source or target Node Class(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             applicationType
             applicableClassName
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" applicableClassName
                                            "classType" "NODE"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (deleteRelation :fromNodeLabels ["Class"]
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabels ["Class"]
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false)
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn addRelApplicableType
  "Add an applicable Source/Target type to a Relation Class, by creating a relation: ApplicableSourceNT/ApplicableTargetNT.
  :className should be className of relation class.(Mandatory)
  :applicationType should be either SOURCE or TARGET as string.(Mandatory)
  :applicableClassName should be a className of the source or target Node Class(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             applicationType
             applicableClassName
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" applicableClassName
                                            "classType" "NODE"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (createRelation :fromNodeLabels ["Class"]
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabels ["Class"]
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false
                                   :unique? true)
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries [builtQuery])
        builtQuery))))

(defn getRelApplicableNTs
  "Get a relation class' ApplicableNTs.
  :className should be of a Class of classType 'RELATION'.(Mandatory)"
  [& {:keys [className
             count?]
      :or {count? false}}]
  {:pre [(string? className)]}
  (let [combinedPropertyMap (combinePropertyMap {"RT" {"className" className
                                                       "classType" "RELATION"}
                                                 "NT" {"classType" "NODE"}}
                                                )
        builtQuery1 {:query (str "MATCH (rt:`Class` "
                                 ((combinedPropertyMap :propertyStringMap) "RT")
                                 ")-[:`ApplicableSourceNT`]->(nt:`Class` "
                                 ((combinedPropertyMap :propertyStringMap) "NT")
                                 ") RETURN "(if count?
                                              "count(nt)"
                                              "nt"))
                     :parameters (combinedPropertyMap :combinedPropertyMap)}
        builtQuery2 {:query (str "MATCH (rt:`Class` "
                                 ((combinedPropertyMap :propertyStringMap) "RT")
                                 ")-[:`ApplicableTargetNT`]->(nt:`Class` "
                                 ((combinedPropertyMap :propertyStringMap) "NT")
                                 ") RETURN "(if count?
                                              "count(nt)"
                                              "nt"))
                     :parameters (combinedPropertyMap :combinedPropertyMap)}
        ]
    ((gdriver/runQuery builtQuery1 builtQuery2) :results)))

(defn addClassAT
  "Adds a relation HasAttributeType from Class to AttributeType.
  :_atname: _name of AttributeType.(Mandatory)
  :_atdatatype: _datatype of AttributeType.(Mandatory)
  :className: className of Class(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [_atname
             className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]  
  {:pre [(string? className)
         (= 1 (count (getNodes :labels ["AttributeType"]
                               :parameters {"_name" _atname}
                               ))
            )
         ]
   }
  (createRelation :fromNodeLabels ["Class"]
                  :fromNodeParameters {"className" className}
                  :relationshipType "HasAttributeType"
                  :relationshipParameters {}
                  :toNodeLabels ["AttributeType"]
                  :toNodeParameters {"_name" _atname}
                  :unique? true
                  :execute? execute?
                  :queryAggregator queryAggregator)
  )

(defn remClassAT
  "Removes relation HasAttributeType from Class to AttributeType.
  :_atname: _name of AttributeType.(Mandatory)
  :_atdatatype: _datatype of AttributeType.(Mandatory)
  :className: className of Class(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [_atname
             className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :labels ["AttributeType"]
                               :parameters {"_name" _atname}
                               ))
            )
         ]
   }
  (deleteRelation :fromNodeLabels ["Class"]
                  :fromNodeParameters {"className" className}
                  :relationshipType "HasAttributeType"
                  :relationshipParameters {}
                  :toNodeLabels ["AttributeType"]
                  :toNodeParameters {"_name" _atname}
                  :execute? execute?
                  :queryAggregator queryAggregator))

(defn addATVR
  "Adds a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.(Mandatory)
  :fnName should be fnName of a CustomFunction.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [_atname
             fnName
             constraintValue
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (createRelation :fromNodeLabels ["CustomFunction"]
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "ValueRestrictionAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabels ["AttributeType"]
                  :toNodeParameters {"_name" _atname}
                  :unique? true
                  :execute? execute?
                  :queryAggregator queryAggregator))

(defn remATVR
  "Removes a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.(Mandatory)
  :fnName should be fnName of a CustomFunction.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [_atname
             fnName
             constraintValue
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (deleteRelation :fromNodeLabels ["CustomFunction"]
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "ValueRestrictionAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabels ["AttributeType"]
                  :toNodeParameters {"_name" _atname}
                  :execute? execute?
                  :queryAggregator queryAggregator))

(defn editATVR
  "Edits a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.(Mandatory)
  :fnName should be fnName of a CustomFunction.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument of existing ValueRestriction(Mandatory)
  :newConstraintValue(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [_atname
             fnName
             constraintValue
             newConstraintValue
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (editRelation :fromNodeLabels ["CustomFunction"]
                :fromNodeParameters {"fnName" fnName}
                :relationshipType "ValueRestrictionAppliesTo"
                :relationshipParameters {"constraintValue" constraintValue}
                :toNodeLabels ["AttributeType"]
                :toNodeParameters {"_name" _atname}
                :newRelationshipParameters {"constraintValue" newConstraintValue}
                :execute? execute?
                :queryAggregator queryAggregator))

(defn editClassNC
  "Edits relation NeoConstraintAppliesTo from a NeoConstraint to a Class.
  :className should be a string.(Mandatory)
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY(Mandatory)
  :constraintTarget should be either of NODE,RELATION.(Mandatory)
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY.(Mandatory)
  :newConstraintValue(Mandatory)
  :schemaQUeryAggregator and :dataQueryAggregator should both be names of existing queryAggregators,
    either both nil or both strings. Make sure you execute both of these."
  [& {:keys [constraintType
             constraintTarget
             constraintValue
             newConstraintValue
             className
             execute?
             schemaQueryAggregator
             dataQueryAggregator]
      :or {execute? true
           schemaQueryAggregator nil
           dataQueryAggregator nil}}
   ]
  {:pre [(string? className)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         (or (every? nil? [schemaQueryAggregator dataQueryAggregator])
             (every? string? [schemaQueryAggregator dataQueryAggregator]))
         (or (and (contains? #{"UNIQUE" "EXISTANCE"} constraintType) (string? newConstraintValue))
             (and (= constraintType "NODEKEY") (coll? newConstraintValue) (every? string? newConstraintValue)))
         ]
   }
  (let [builtQueries [[(editRelation :fromNodeLabels ["NeoConstraint"]
                                     :fromNodeParameters {"constraintType" constraintType
                                                          "constraintTarget" constraintTarget}
                                     :relationshipType "NeoConstraintAppliesTo"
                                     :relationshipParameters {"constraintValue" constraintValue}
                                     :toNodeLabels ["Class"]
                                     :toNodeParameters {"className" className
                                                        "classType" constraintTarget}
                                     :newRelationshipParameters {"constraintValue" newConstraintValue}
                                     :execute? false)
                       ]
                      (reduceQueryColl [(exemptClassNeoConstraint :className className
                                                                  :constraintType constraintType
                                                                  :constraintTarget constraintTarget
                                                                  :constraintValue constraintValue
                                                                  :execute? false)
                                        (applyClassNeoConstraint :className className
                                                                 :constraintType constraintType
                                                                 :constraintTarget constraintTarget
                                                                 :constraintValue newConstraintValue
                                                                 :execute? false
                                                                 )
                                        ]
                                       )
                      ]
        ]
    (if execute?
      (apply gdriver/runTransactions builtQueries)
      (if (and schemaQueryAggregator dataQueryAggregator)
        (do (queryAggregator/addQueries :qaName dataQueryAggregator :queries (builtQueries 0))
            (queryAggregator/addQueries :qaName schemaQueryAggregator :queries (builtQueries 1)))
        builtQueries))))

(defn getNeoConstraintsWithAT
  "Get NeoConstraint that are applied with a particular AttributeType
  :atName string (Mandatory)"
  [& {:keys [atName
             count?]
      :or {count? false}}]
  {:pre [(string? atName)]}
  (apply concat ((gdriver/runQuery {:query (str "MATCH (neo:`NeoConstraint` {constraintType:\"NODEKEY\"})-[rel:`NeoConstraintAppliesTo`]->(cl:`Class`)"
                                                " WHERE {ATT} IN rel.constraintValue"
                                                " RETURN "
                                                (if count?
                                                  "count(neo)"
                                                  "cl.className,neo.constraintType,neo.constraintTarget,rel.constraintValue")
                                                )
                                    :parameters {"ATT" atName}}
                                   {:query (str "MATCH (neo:`NeoConstraint`)-[rel:`NeoConstraintAppliesTo`]->(cl:`Class`)"
                                                " WHERE {ATT} IN rel.constraintValue and"
                                                " neo.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                                                " RETURN "
                                                (if count?
                                                  "count(neo)"
                                                  "cl.className,neo.constraintType,neo.constraintTarget,rel.constraintValue"))
                                    :parameters {"ATT" atName}}) :results)))

(defn createDelATNC
  "Creates a query to remove an AttributeType in all relations with label NeoConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.(Mandatory)"
  [& {:keys [atName]}
   ]
  {:pre [string? atName]}
  (let [propertyMap {"ATT" atName}]
    [{:query (str "MATCH (neo1:`NeoConstraint` {constraintType:\"NODEKEY\"})-[rel1:`NeoConstraintAppliesTo`]->(cl1:`Class`)"
                  " "(createPropListEditString :varName "rel1"
                                               :propName "constraintValue"
                                               :editType "DELETE"
                                               :editVal "ATT"
                                               :withWhere? false)
                  " DELETE rel1"
                  (createReturnString ["cl1" "UUID" "UUID"]))
      :doRCS? true
      :rcs-vars ["UUID"]
      :parameters propertyMap}
     {:query (str "MATCH"
                  " (neo2:`NeoConstraint`)-[rel2:`NeoConstraintAppliesTo`]->(cl2:`Class`)"
                  " WHERE neo2.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                  " AND {ATT} IN rel2.constraintValue"
                  " DELETE rel2"
                  (createReturnString ["cl2" "UUID" "UUID"]))
      :doRCS? true
      :rcs-vars ["UUID"]
      :parameters propertyMap}])
  )

(defn createReplaceATNC
  "Creates a query to replace an AttributeType in all relations with label NeoConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.(Mandatory)
  :renameName should be a string, replacement _name(Mandatory)"
  [& {:keys [atName
             renameName]}]
  {:pre [(string? atName)
         (string? renameName)]}
  (let [propertyMap {"ATT" atName "att" renameName}]
    {:query (str "MATCH (neo1:`NeoConstraint` {constraintType:\"NODEKEY\"})-[rel1:`NeoConstraintAppliesTo`]->(cl1:`Class`),"
                 " (neo2:`NeoConstraint`)-[rel2:`NeoConstraintAppliesTo`]->(cl2:`Class`)"
                 " WHERE neo2.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                 " AND {ATT} IN rel1.constraintValue"
                 " AND {ATT} IN rel2.constraintValue"
                 " "(createPropListEditString :varName "rel1"
                                              :propName "constraintValue"
                                              :editType "REPLACE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)","
                 " rel2.constraintValue={att}"
                 (createReturnString ["cl1" "UUID" "UUID1"] ["cl2" "UUID" "UUID2"]))
     :doRCS? true
     :rcs-vars ["UUID1" "UUID2"]
     :parameters propertyMap})
  )

(defn remClassNC
  "Removes relation NeoConstraintAppliesTo from a NeoConstraint to a Class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.(Mandatory)
  :constraintTarget should be either of NODE,RELATION.(Mandatory)
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY(Mandatory)
  :schemaQUeryAggregator and :dataQueryAggregator should both be names of existing queryAggregators,
    either both nil or both strings. Make sure you execute both of these."
  [& {:keys [constraintType
             constraintTarget
             constraintValue
             className
             execute?
             schemaQueryAggregator
             dataQueryAggregator]
      :or {execute? true
           dataQueryAggregator nil
           schemaQueryAggregator nil}}
   ]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         ]
   }
  (let [builtQueries [[(deleteRelation :fromNodeLabels ["NeoConstraint"]
                                       :fromNodeParameters {"constraintType" constraintType
                                                            "constraintTarget" constraintTarget}
                                       :relationshipType "NeoConstraintAppliesTo"
                                       :relationshipParameters {"constraintValue" constraintValue}
                                       :toNodeLabels ["Class"]
                                       :toNodeParameters {"className" className}
                                       :execute? false)]
                      (exemptClassNeoConstraint :className className
                                                :constraintType constraintType
                                                :constraintTarget constraintTarget
                                                :constraintValue constraintValue
                                                :execute? false)
                      ]
        ]
    (if execute?
      (apply gdriver/runTransactions builtQueries)
      (if (and schemaQueryAggregator dataQueryAggregator)
        (do (queryAggregator/addQueries :qaName dataQueryAggregator :queries (builtQueries 0))
            (queryAggregator/addQueries :qaName schemaQueryAggregator :queries (builtQueries 1)))
        builtQueries))))

(defn addClassNC
  "Adds a relation NeoConstraintAppliesTo from NeoConstraint to Class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.(Mandatory)
  :constraintTarget should be either of NODE,RELATION.(Mandatory)
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY(Mandatory)
  :schemaQUeryAggregator and :dataQueryAggregator should both be names of existing queryAggregators,
    either both nil or both strings. Make sure you execute both of these."
  [& {:keys [constraintType
             constraintTarget
             constraintValue
             className
             execute?
             schemaQueryAggregator
             dataQueryAggregator]
      :or {execute? true
           dataQueryAggregator nil
           schemaQueryAggregator nil}}
   ]
  {:pre [(string? className)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         (or (and (contains? #{"UNIQUE" "EXISTANCE"} constraintType) (string? constraintValue))
             (and (= constraintType "NODEKEY") (coll? constraintValue) (every? string? constraintValue)))
         ]
   }
  (let 	[createRelationQuery
         (createRelation :fromNodeLabels ["NeoConstraint"]
                         :fromNodeParameters {"constraintType" constraintType
                                              "constraintTarget" constraintTarget}
                         :relationshipType "NeoConstraintAppliesTo"
                         :relationshipParameters {"constraintValue" constraintValue}
                         :toNodeLabels ["Class"]
                         :toNodeParameters {"className" className}
                         :execute? false)
         applyClassNeoConstraintQuery (applyClassNeoConstraint 	:className className
                                                                :constraintType constraintType 
                                                                :constraintTarget constraintTarget 
                                                                :constraintValue constraintValue 
                                                                :execute? false)
         combinedQuery (vec (conj applyClassNeoConstraintQuery
                                  createRelationQuery)
                            )
         builtQueries [(conj [] createRelationQuery)
                       (vec applyClassNeoConstraintQuery)]
         ]
    (if execute?
      (apply gdriver/runTransactions builtQueries)	
      (if (and schemaQueryAggregator dataQueryAggregator)
        (do (queryAggregator/addQueries :qaName	dataQueryAggregator :queries (builtQueries 0))
            (queryAggregator/addQueries :qaName schemaQueryAggregator :queries (builtQueries 1)))
        builtQueries))))

(defn addClassCC
  "Adds a relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.(Mandatory)
  :atList should be list of AttributeTypes' _name.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument(Mandatory)
  :queryAggregator should be the name of an existing dataQueryAggregator"
  [& {:keys [fnName
             atList
             constraintValue
             className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)]}
  (let [classAttributeTypes (getClassAttributeTypes :className className)]
    (if (not (every? #(= 1 (count (filter
                                   (fn
                                     [at]
                                     (= % ((into {} at) "_name")
                                        )
                                     ) classAttributeTypes)
                                  )
                         ) atList)
             )
      (throw (Exception. (str "atList must contain _name's of an AttributeType :" atList))))
    (createRelation :fromNodeLabels ["CustomFunction"]
                    :fromNodeParameters {"fnName" fnName}
                    :relationshipType "CustomConstraintAppliesTo"
                    :relationshipParameters {"atList" atList
                                             "constraintValue" constraintValue}
                    :toNodeLabels ["Class"]
                    :toNodeParameters {"className" className}
                    :execute? execute?
                    :queryAggregator queryAggregator)))

(defn remClassCC
  "Delete relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.(Mandatory)
  :atList should be list of AttributeTypes' _name.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument(Mandatory)
  :queryAggregator should be the name of an existing dataQueryAggregator"
  [& {:keys [fnName
             atList
             constraintValue
             className
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)]}
  (deleteRelation :fromNodeLabels ["CustomFunction"]
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "CustomConstraintAppliesTo"
                  :relationshipParameters {"atList" atList
                                           "constraintValue" constraintValue}
                  :toNodeLabels ["Class"]
                  :toNodeParameters {"className" className}
                  :execute? execute?
                  :queryAggregator queryAggregator))

(defn editClassCC
  "Edit relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.(Mandatory)
  :className of Class(Mandatory)
  :atList should be list of AttributeTypes' _name of existing CCAT relation.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument of existing CCAT relation.(Mandatory)
  :editMap map with keys 'atList' and 'constraintValue' and appropriate values(Mandatory)
  :queryAggregator should be the name of an existing dataQueryAggregator"
  [& {:keys [fnName
             atList
             constraintValue
             className
             editMap
             execute?
             queryAggregator]
      :or {execute? true
           queryAggregator nil}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)
         (map? editMap)
         (clojure.set/subset? (keys editMap) #{"atList" "constraintValue"})
         (or (coll? (editMap "atList")) (nil? (editMap "atList")))]}
  (editRelation :fromNodeLabels ["CustomFunction"]
                :fromNodeParameters {"fnName" fnName}
                :relationshipType "CustomConstraintAppliesTo"
                :relationshipParameters {"atList" atList
                                         "constraintValue" constraintValue}
                :toNodeLabels ["Class"]
                :toNodeParameters {"className" className}
                :newRelationshipParameters editMap
                :execute? execute?
                :queryAggregator queryAggregator))

(defn createDelATCC
  "Creates a query to remove an AttributeType in all relations with label CustomConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.(Mandatory)"
  [& {:keys [atName]}
   ]
  {:pre [string? atName]}
  (let [propertyMap {"ATT" atName}]
    {:query (str "MATCH (cc:`CustomFunction`)-[rel:`CustomConstraintAppliesTo`]->(cl:`Class`)"
                 " WHERE {ATT} IN rel.atList"
                 " "(createPropListEditString :varName "rel"
                                              :propName "atList"
                                              :editType "DELETE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)
                 (createReturnString ["cl" "UUID" "UUID"]))
     :doRCS? true
     :rcs-vars ["UUID"]
     :parameters propertyMap})
  )

(defn createReplaceATCC
  "Creates a query to replace an AttributeType in all relations with label CustomConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.(Mandatory)
  :renameName should be a string, replacement _name(Mandatory)"
  [& {:keys [atName
             renameName]}]
  {:pre [(string? atName)
         (string? renameName)]}
  (let [propertyMap {"ATT" atName "att" renameName}]
    {:query (str "MATCH (cc:`CustomFunction`)-[rel:`CustomConstraintAppliesTo`]->(cl:`Class`)"
                 " WHERE {ATT} IN rel.atList"
                 " "(createPropListEditString :varName "rel"
                                              :propName "atList"
                                              :editType "REPLACE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)
                 (createReturnString ["cl" "UUID" "UUID"]))
     :doRCS? true
     :rcs-vars ["UUID"]
     :parameters propertyMap})
  )

(defn- addSubTypeVRQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass NeoConstraints to the subclass"
  [& {:keys [_name
             _datatype
             subTypeOf]}]
  (let
      [[superTypeName] subTypeOf
       superTypeVRVec (vec (((getATValueRestrictions :atName (str superTypeName)) :results) 0))
       is_aRelationQuery (createRelation 	
                          :fromNodeLabels ["AttributeType"]
                          :fromNodeParameters {"_name" _name}
                          :relationshipType "is_a"
                          :relationshipParameters {}
                          :toNodeLabels ["AttributeType"]
                          :toNodeParameters {"_name" superTypeName}
                          :execute? false
                          )
       VRQueriesVec (into [is_aRelationQuery]
                          (map (fn
                                 [SingleVRMap]
                                 (addATVR :_atname _name
                                          :fnName (((SingleVRMap "cf") :properties) "fnName")
                                          :constraintValue (((SingleVRMap "vr") :properties) "constraintValue")
                                          :execute? false)
                                 )
                               superTypeVRVec
                               )
                          )]
    VRQueriesVec	
    )
  )

(defn createAttributeType
  "Creates a node with Label AttributeType.
  :subTypeOf should be a vector containing the name of the superType if any(Mandatory)
  :_name should be a string(Mandatory)
  :_datatype should be a string of one of the following: 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'.(Mandatory)
  :subjectQualifier should be a list of strings.
  :attributeQualifier should be a list of strings.
  :valueQualifier should be a list of strings
  :queryAggregator should be the name of an existing dataQueryAggregator"
  [& {:keys [_name
             _datatype
             subTypeOf
             subjectQualifier
             attributeQualifier
             valueQualifier
             execute?
             subTypeOf
             queryAggregator]
      :or {execute? true
           subjectQualifier []
           attributeQualifier []
           valueQualifier []
           subTypeOf []
           queryAggregator nil}
      :as keyArgs}]
  {:pre [
         (string? _name)
         (contains? validATDatatypes _datatype)
         ]
   }
  (let [createNewNodeQuery 
        (createNewNode :labels ["AttributeType"]
                       :parameters {"_name" _name
                                    "_datatype" _datatype
                                    "subjectQualifier" subjectQualifier
                                    "attributeQualifier" attributeQualifier
                                    "valueQualifier" valueQualifier}
                       :execute? false)]
    (if (not (empty? subTypeOf))
                                        ;"Adds the attributes,NeoConstraints and CustomConstraints of the superclass to the subclass"
      (let
          [completeQueryVec (vec
                             (concat [createNewNodeQuery]
                                     (addSubTypeVRQueryVec :_name _name
                                                           :_datatype _datatype
                                                           :subTypeOf subTypeOf)
                                     )
                             )
           [superTypeName] subTypeOf]
        (if (not (empty? (getNodes :labels ["AttributeType"]
                                   :parameters {"_name" (str superTypeName)}
                                   :execute? true)
                         )
                 )
          (if
              execute?
            (apply gdriver/runQuery completeQueryVec)
            completeQueryVec	
            )
          (gdriver/runQuery)
          )
        )
      (if
          execute?
        (gdriver/runQuery createNewNodeQuery)
        createNewNodeQuery
        )
      )
    )
  )

(defn getATClasses
  "Get classes that have a particular attributeType.
  :_name should be a string, name of an AttributeType.(Mandatory)"
  [& {:keys [_name
             count?]
      :or {count? false}}]
  {:pre [(string? _name)]}
  (((gdriver/runQuery {:query (str "MATCH (att:`AttributeType` {_name:{_name}})<-[:`HasAttributeType`]-(n:`Class`) RETURN "(if count?
                                                                                                                             "count(n)"
                                                                                                                             "n"))
                       :parameters {"_name" _name}}) :results) 0)
  )

(defn getAttributeTypes
  "Fetches all AttributeTypes from db"
  [& {:keys [count?]
      :or {count? false}}]
  (getNodes :labels ["AttributeType"]
            :count? count?))

(defn editAttributeType
  "Edit an attributeType.
  `:editChanges`(Mandatory) should be a map with at least one of the following keys :
  -`_name` should be a string(Mandatory)
  -`_datatype` should be a string of one of the following: 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'.(Mandatory)
  -`subjectQualifier` should be a list of strings.
  -`attributeQualifier` should be a list of strings.
  -`valueQualifier` should be a list of strings.
  CAREFUL when using forceMigrate... it will edit ALL nodes/relations with this attributeType, and automatically DROP and re-CREATE all constraints with the particular AttributeType.
  Currently will not check if there is actually a change in the AttributeType.
  If _datatype is a key in `:editChanges`, it will change the corresponding value in class instances to the default value, even though _datatype is the same.
  Using `:forceMigrate?` in editAttributeType is not advised when there are too many instances and constraints with the attributeType ... It would take WAYYY too much time.
  Also, when changing _datatype, if the AttributeType has some unique/nodekey constraint, the instances of the AT will not be edited, as the default values are non-unique.
  But, if _name and _datatype is being changed together, the instances will be updated, but the new constraints will not be created.
  One must manually edit all the instances to fit the constraints and then call `applyClassNeoConstraints` with the approproate `:className`
  Since this function is complicated, queryAggregation is too complicated as well. Good Luck."
  [& {:keys [_name
             editChanges
             forceMigrate?
             execute?]
      :or {forceMigrate? false
           execute? true}}
   ]
  {:pre [(string? _name)
         (map? editChanges)
         (clojure.set/subset? (into #{} (keys editChanges))
                              #{"_name"
                                "_datatype"
                                "subjectQualifier"
                                "attributeQualifier"
                                "valueQualifier"})
         (or (not (contains? editChanges "_name")) (and (contains? editChanges "_name") (string? (editChanges "_name"))))
         (or (not (contains? editChanges "_datatype")) (and (contains? editChanges "_datatype") (contains? validATDatatypes (editChanges "_datatype"))))
         ]
   }
  (let [editQuery (editNodeProperties :labels ["AttributeType"]
                                      :parameters {"_name" _name}
                                      :changeMap editChanges
                                      :execute? false)
        ATClasses (getATClasses :_name _name)
        ATClassNames (map #(((% "n") :properties) "className") ATClasses)]
    (if (or (empty? ATClasses)
            (clojure.set/subset?
             (into #{} (keys editChanges))
             #{"subjectQualifier"
               "attributeQualifier"
               "valueQualifier"}
             )
            )
      (if execute?
        (gdriver/runQuery editQuery)
        editQuery)
      (if
          (not forceMigrate?)
        (throw (Exception. (str "Class(es) "(seq
                                             (map #((into {} ((% "n") :properties))
                                                    "className")
                                                  ATClasses)
                                             )
                                " have `"_name"`, use `:forceMigrate?` true to make functional changes to the class and it's instances automatically.")
                           )
               )
        (let [nameChangeQueries (if (contains? editChanges "_name")
                                  (let [neoConstraintsWithAT (getNeoConstraintsWithAT :atName _name)
                                        constraintDropQueries 
                                        (reduceQueryColl (map #(exemptClassNeoConstraint :execute? false
                                                                                         :className (% "cl.className")
                                                                                         :constraintTarget (% "neo.constraintTarget")
                                                                                         :constraintValue (% "rel.constraintValue")
                                                                                         :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
                                        constraintCreateQueries (reduceQueryColl (map #(applyClassNeoConstraint :execute? false
                                                                                                                :className (% "cl.className")
                                                                                                                :constraintTarget (% "neo.constraintTarget")
                                                                                                                :constraintValue (if (= (type (% "rel.constraintValue")) java.util.Collections$UnmodifiableRandomAccessList)
                                                                                                                                   (editCollection :coll (into [] (% "rel.constraintValue"))
                                                                                                                                                   :editType "REPLACE"
                                                                                                                                                   :editVal _name
                                                                                                                                                   :replaceVal (editChanges "_name"))
                                                                                                                                   (editChanges "_name"))
                                                                                                                :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
                                        dataEditQueries   (reduceQueryColl [(createReplaceATNC :atName _name
                                                                                               :renameName (editChanges "_name"))
                                                                            (createReplaceATCC :atName _name
                                                                                               :renameName (editChanges "_name"))
                                                                            {:query (str "MATCH (node) WHERE "
                                                                                         (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                                                         " AND "(createRenameString :addWhere? false
                                                                                                                    :varName "node"
                                                                                                                    :renameMap {_name (editChanges "_name")})
                                                                                         (createReturnString ["node" "UUID" "UUID"]))
                                                                             :doRCS? true
                                                                             :rcs-vars ["UUID"]
                                                                             :parameters {}}])
                                        ]
                                    {:constraintDropQueries constraintDropQueries
                                     :constraintCreateQueries constraintCreateQueries
                                     :dataEditQueries dataEditQueries})
                                  {:constraintDropQueries []
                                   :constraintCreateQueries []
                                   :dataEditQueries []}
                                  )
              datatypeChangeQueries (if (contains? editChanges "_datatype")
                                      (let [dataEditQueries [{:query (str "MATCH (node) WHERE "
                                                                          (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                                          " SET node."(or (editChanges "_name") _name)" = {defVal}"
                                                                          (createReturnString ["node" "UUID" "UUID"]))
                                                              :doRCS? true
                                                              :rcs-vars ["UUID"]
                                                              :parameters {"defVal" (defaultDatatypeValues (editChanges "_datatype"))}}]]
                                        {:constraintDropQueries []
                                         :constraintCreateQueries []
                                         :dataEditQueries dataEditQueries})
                                      {:constraintDropQueries []
                                       :constraintCreateQueries []
                                       :dataEditQueries []})]
          (let [constraintDropQueries (concat (nameChangeQueries :constraintDropQueries)
                                              (datatypeChangeQueries :constraintDropQueries))
                dataEditQueries (conj (concat (nameChangeQueries :dataEditQueries)
                                              (datatypeChangeQueries :dataEditQueries))
                                      editQuery)
                constraintCreateQueries (concat (nameChangeQueries :constraintCreateQueries)
                                                (datatypeChangeQueries :constraintCreateQueries))]
            (if
                execute?
              (gdriver/runTransactions constraintDropQueries dataEditQueries constraintCreateQueries)
              {:constraintDropQueries constraintDropQueries
               :dataEditQueries dataEditQueries
               :constraintCreateQueries constraintCreateQueries}))
          )
        )
      )
    )
  )
(defn deleteAttributeType
  "Delete an AttributeType.
  :forceMigrate?"
  [&{:keys [_name
            forceMigrate?
            execute?]
     :or {execute? true}}]
  {:pre [(string? _name)]}
  (let [deleteQueries (deleteDetachNodes :labels ["AttributeType"]
                                         :parameters {"_name" _name}
                                         :execute? false)
        ATClasses (getATClasses :_name _name)
        ATClassNames (map #(((% "n") :properties) "className") ATClasses)]
    (if (empty? ATClasses)
      (if execute?
        (apply gdriver/runQuery deleteQueries)
        deleteQueries)
      (if (not forceMigrate?)
        (throw (Exception. (str "Class(es) "(seq
                                             (map #((into {} ((% "n") :properties))
                                                    "className")
                                                  ATClasses)
                                             )
                                " have `"_name"`, use `:forceMigrate?` true to make functional changes to the class and it's instances automatically.")
                           )
               )
        (let [neoConstraintsWithAT (getNeoConstraintsWithAT :atName _name)
              constraintDropQueries (reduceQueryColl (map #(exemptClassNeoConstraint :execute? false
                                                                                     :className (% "cl.className")
                                                                                     :constraintTarget (% "neo.constraintTarget")
                                                                                     :constraintValue (% "rel.constraintValue")
                                                                                     :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
              constraintCreateQueries (reduceQueryColl (map #(if (= "NODEKEY" (% "neo.constraintType"))
                                                               (applyClassNeoConstraint :execute? false
                                                                                        :className (% "cl.className")
                                                                                        :constraintTarget (% "neo.constraintTarget")
                                                                                        :constraintValue (editCollection :coll (into [] (% "rel.constraintValue"))
                                                                                                                         :editType "DELETE"
                                                                                                                         :editVal _name)
                                                                                        :constraintType (% "neo.constraintType"))) neoConstraintsWithAT))
              dataEditQueries (reduceQueryColl (concat deleteQueries
                                                     [(createDelATNC :atName _name)
                                                      (createDelATCC :atName _name)
                                                      {:query (str "MATCH (node) WHERE "
                                                                   (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                                   " "(createRemString :varName "node"
                                                                                       :remPropertyList [_name])
                                                                   (createReturnString ["node" "UUID" "UUID"]))
                                                       :doRCS? true
                                                       :rcs-vars ["UUID"]
                                                       :parameters {}}]))]
          (if
              execute?
            (gdriver/runTransactions constraintDropQueries dataEditQueries constraintCreateQueries)
            {:constraintDropQueries constraintDropQueries
             :dataEditQueries dataEditQueries
             :constraintCreateQueries constraintCreateQueries}
            )
          )
        )
      )
    )
  )

(defn- addSubClassATQueryVec
  "Returns a vector of queries consisting of is_aRelationship 
  query and the queries for adding superclass AttributeTypes to the subclass"
  [& {:keys [className
             subClassOf]}] 
  (let [[superClassName] subClassOf
        superClassATVec (vec (getClassAttributeTypes :className (str superClassName))) 
        is_aRelationQuery 	
        (createRelation 	
         :fromNodeLabels ["Class"]
         :fromNodeParameters {"className" className}
         :relationshipType "is_a"
         :relationshipParameters {}
         :toNodeLabels ["Class"]
         :toNodeParameters {"className" superClassName}
         :execute? false
         )
        queriesVec
        (into [is_aRelationQuery]
              (map (fn
                     [SingleATMap] 
                     (addClassAT :_atname (SingleATMap "_name")
                                 :className className
                                 :execute? false)
                     )
                   superClassATVec
                   )
              )]
    queriesVec	
    )
  )

(defn- addClassNCQuery
  "Returns addClassNC query without doing a check on the existence and the uniqueness of the class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.(Mandatory)
  :constraintTarget should be either of NODE,RELATION.(Mandatory)
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY(Mandatory)"
  [& {:keys [constraintType
             constraintTarget
             constraintValue
             className]}]
  (let 	[createRelationQuery (createRelation :fromNodeLabels ["NeoConstraint"]
                                             :fromNodeParameters {"constraintType" constraintType
                                                                  "constraintTarget" constraintTarget}
                                             :relationshipType "NeoConstraintAppliesTo"
                                             :relationshipParameters {"constraintValue" constraintValue}
                                             :toNodeLabels ["Class"]
                                             :toNodeParameters {"className" className}
                                             :execute? false)
         applyClassNeoConstraintQuery (applyClassNeoConstraint 	:className className 
                                                                :constraintType constraintType 
                                                                :constraintTarget constraintTarget 
                                                                :constraintValue constraintValue 
                                                                :execute? false)
         combinedQuery (conj (conj []
                                   (vec applyClassNeoConstraintQuery)
                                   )
                             (conj [] createRelationQuery)
                             )
         ]
    combinedQuery
    )
  )

;;;;;;Try to improve efficiency
(defn- addSubClassNCQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass NeoConstraints to the subclass"
  [& {:keys [className
             subClassOf
             classType]}
   ]
  (let
      [[superClassName] subClassOf
       superClassNCVec (vec (((getClassNeoConstraints :className (str superClassName)) :results) 0))
       addClassNCQueryVec (vec (into []
                                     (map (fn
                                            [SingleNCMap]
                                            (let [[Query1Vec Query2Vec] (addClassNCQuery :constraintType (((SingleNCMap "neo") :properties) "constraintType")
                                                                                         :constraintTarget classType
                                                                                         :constraintValue (((SingleNCMap "ncat") :properties) "constraintValue")
                                                                                         :className className
                                                                                         :execute? false)
                                                  ]
                                              Query1Vec
                                              )
                                            )
                                          superClassNCVec
                                          )
                                     )	
                               )
       completeNCQueryVec
       (vec 	(into addClassNCQueryVec 
                      (
                       map (fn
                             [SingleNCMap]
                             (let [[Query1Vec Query2Vec] (addClassNCQuery :constraintType (((SingleNCMap "neo") :properties) "constraintType")
                                                                          :constraintTarget classType
                                                                          :constraintValue (((SingleNCMap "ncat") :properties) "constraintValue")
                                                                          :className className
                                                                          :execute? false)
                                   ]
                               Query2Vec
                               )
                             )
                       superClassNCVec
                       )
                      )	
                )	
       ]
    completeNCQueryVec	
    )
  )

(defn- addClassCCQuery
  "Returns query for addClassCC without doing a check on atList
  :fnName of a CustomFunction.(Mandatory)
  :constraintValue should be value to be passed as CustomFunction's second argument(Mandatory)"
  [& {:keys [fnName
             atList
             constraintValue
             className]}]
  {:pre [(string? className)
         (string? fnName)]}
  (createRelation 
   :fromNodeLabels ["CustomFunction"]
   :fromNodeParameters {"fnName" fnName}
   :relationshipType "CustomConstraintAppliesTo"
   :relationshipParameters {"atList" atList
                            "constraintValue" constraintValue}
   :toNodeLabels ["Class"]
   :toNodeParameters {"className" className}
   :execute? false
   )
  )

(defn- addSubClassCCQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass CustomConstraints to the subclass"
  [& {:keys [className
             subClassOf]}
   ]
  (let
      [[superClassName] subClassOf
       superClassCCVec (vec (((getClassCustomConstraints :className (str superClassName)) :results) 0))
       CCQueriesVec (into []
                          (map (fn
                                 [SingleCCMap]
                                 (addClassCCQuery :fnName (((SingleCCMap "cf") :properties) "fnName")
                                                  :atList (vec (((SingleCCMap "ccat") :properties) "atList"))
                                                  :constraintValue (((SingleCCMap "ccat") :properties) "constraintValue")
                                                  :className className)
                                 )
                               superClassCCVec
                               )
                          )]
    CCQueriesVec	
    )
  )

(defn- addRelApplicableTypeQuery
  "Returns the query of the function addRelApplicableType without doing a check 
  on the existence and uniqueness of applicableClass.
  :className should be className of relation class.(Mandatory)
  :applicationType should be either SOURCE or TARGET as string.(Mandatory)
  :applicableClassName should be a className of the source or target Node Class(Mandatory)"
  [& {:keys [className
             applicationType
             applicableClassName]}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (createRelation :fromNodeLabels ["Class"]
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabels ["Class"]
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false
                                   :unique? true)
        ]
    builtQuery
    )
  )

(defn- addSubClassAppTypeQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass ApplicableTypeRelations to the subclass"
  [& {:keys [className subClassOf]}]
  (let
      [[superClassName] subClassOf
       superClassAppSourceNTVec (vec (getClassApplicableSourceNT :className (str superClassName)))
       AppSourceNTQueriesVec (into []
                                   (map (fn
                                          [SingleMap]
                                          (addRelApplicableTypeQuery :className (SingleMap "className")
                                                                     :applicationType "Source"
                                                                     :applicableClassName className)
                                          )
                                        superClassAppSourceNTVec
                                        )
                                   )
       superClassAppTargetNTVec (vec (getClassApplicableTargetNT :className (str superClassName)))
       AppNTQueriesVec (into AppSourceNTQueriesVec
                             (map (fn
                                    [SingleMap]
                                    (addRelApplicableTypeQuery :className (SingleMap "className")
                                                               :applicationType "Target"
                                                               :applicableClassName className)
                                    )
                                  superClassAppTargetNTVec
                                  )
                             )
       ]
    AppNTQueriesVec	
    )
  )

(defn createClass
  "Create a node with label Class
  :subClassOf should be a vector containing the name of the superclass if any(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             classType
             isAbstract?
             subClassOf
             properties
             execute?
             queryAggregator]
      :or {execute? true
           subClassOf []
           queryAggregator nil}
      }
   ]
  {:pre [
         (string? className)
         (contains? #{"NODE" "RELATION"} classType)
         (not
          (or (contains? properties "className")
              (contains? properties "classType")
              (contains? properties "isAbstract")
              )
          )
       	 ]
   }
  (let [createNewNodeQuery 
        (createNewNode 	:labels ["Class"]
                        :parameters (assoc properties
                                           "className" className
                                           "classType" classType
                                           "isAbstract" isAbstract?
                                           )
                        :execute? false
                        :unique? true
			)
        ]
    (if (not (empty? subClassOf))
                                        ;"Adds the attributes,NeoConstraints,CustomConstraints and ApplicableRelationNTs of the superclass to the subclass"
      (let
          [completeQueryVec (vec
                             (concat [createNewNodeQuery]
                                     (addSubClassATQueryVec :className className
                                                            :subClassOf subClassOf)
                                     (addSubClassCCQueryVec :className className
                                                            :subClassOf subClassOf)
                                     (addSubClassAppTypeQueryVec :className className
                                                                 :subClassOf subClassOf)
                                     )		
                             )
           NCQueryVec (addSubClassNCQueryVec :className className
                                             :subClassOf subClassOf
                                             :classType classType)
           [superClassName] subClassOf]
        (if (not (empty? (getNodes :labels ["Class"]
                                   :parameters {"className" (str superClassName)}
                                   :execute? true)
                         )
                 )
          (if execute?
            (apply gdriver/runTransactions completeQueryVec NCQueryVec)
            (if queryAggregator
              (queryAggregator/addQueries :qaName queryAggregator :queries (into completeQueryVec NCQueryVec))
	      (into completeQueryVec NCQueryVec)
              ))
          (throw (Exception. (str "Class `" superClassName "` Does not seem to exist")))
          ))
      (if execute?
        (gdriver/runQuery createNewNodeQuery)
        (if queryAggregator
          (queryAggregator/addQueries :qaName queryAggregator :queries [createNewNodeQuery])
          createNewNodeQuery)))))

(defn getClassType
  "Gets the classType of a Class
  :className string (Mandatory)"
  [&{:keys [className]}]
  {:pre [(string? className)]}
  (try ((into {} ((first (getNodes :labels ["Class"]
                                   :parameters {"className" className}
                                   )
                         ) :properties)
              ) "classType")
       (catch Exception E
         (throw (Exception.
                 (str "Failed to fetch classType"
                      (.getMessage E)
                      )
                 )
                )
         )
       )
  )

(defn getClassInstances
  "Get Class Instances
  :className string (Mandatory)
  :parameters should be a map"
  [& {:keys [className
             parameters
             count?]
      :or {count false
           parameters {}}}]
  {:pre [(string? className)]}
  (if (= "NODE" (getClassType :className className))
    (getNodes :count? count? :labels [className]
              :parameters parameters)
    (getRelations :count? count?
                  :relationshipType className
                  :relationshipParameters parameters)
    )
  )

(defn editClass
  "Edit isAbstract,className, miscelaneous properties of a class.
  :className should be string , name of class to edit.(Mandatory)
  :newProperties should be a map with optional keys:(Mandatory)
  -'className'
  -'isAbstract'
  :miscProperties should be a map with optional keys other than:
  -'className'
  -'classType'
  -'isAbstract'
  Changing isAbstract to true will delete all instances of the class, including relations, if the class is a node class.
  Use :forceMigrate? if you want the changes to effect instances as well. eg, if you change isAbstract? to true, then it will
  delete all instances.
  Since this function is complicated, queryAggregation is too complicated as well. Good Luck."
  [& {:keys [className
             newProperties
             miscProperties
             forceMigrate?
             execute?]
      :or {newProperties {}
           miscProperties {}
           forceMigrate? false
           execute? true}}]
  {:pre [(string? className)
         (clojure.set/subset? (into #{} (keys newProperties)) #{"className" "isAbstract"})
         (empty? (clojure.set/intersection (into #{} (keys miscProperties)) #{"className" "isAbstract" "classType" "UUID"}))]}
  (let [classType (getClassType :className className)
        editClassQuery (editNodeProperties :labels ["Class"]
                                           :parameters {"className" className}
                                           :changeMap (merge newProperties
                                                             miscProperties)
                                           :execute? false)]
    (if (empty? newProperties)
      (if execute?
        (gdriver/runQuery editClassQuery)
        editClassQuery)
      (if (or (= 0 (first (getClassInstances :className className
                                             :count? true)
                          )
                 ) forceMigrate?)
        (let [dataEditQueries (reduceQueryColl (concat [editClassQuery]
                                                       (if (and (contains? newProperties "isAbstract")
                                                                (= true (newProperties "isAbstract")))
                                                         (case classType
                                                           "NODE" (deleteDetachNodes :labels [className]
                                                                                     :parameters {}
                                                                                     :execute? false)
                                                           "RELATION" [(deleteRelation :relationshipType className
                                                                                       :execute? false)]
                                                           )
                                                         []
                                                         )
                                                       [(if (contains? newProperties "className")
                                                          (renameLabels :labels [className]
                                                                        :properties {}
                                                                        :objectType classType
                                                                        :replaceLabelMap {className (newProperties "className")}
                                                                        :execute? false)
                                                          []
                                                          )]
                                                       ))
              constraintDropQueries (reduceQueryColl (if (contains? newProperties "className")
                                                       (exemptClassNeoConstraints :className className
                                                                                  :execute? false)
                                                       []))
              constraintCreateQueries (reduceQueryColl (if (contains? newProperties "className")
                                                         (map (fn [nc] (let [neoConstraint (merge ((into {} (nc "ncat")) :properties)
                                                                                                  ((into {} (nc "neo")) :properties))]
                                                                         (applyClassNeoConstraint :className (newProperties "className")
                                                                                                  :constraintValue (neoConstraint "constraintValue")
                                                                                                  :constraintType (neoConstraint "constraintType")
                                                                                                  :constraintTarget (neoConstraint "constraintTarget")
                                                                                                  :execute? false)
                                                                         )
                                                                )
                                                              (first ((getClassNeoConstraints :className className) :results)
                                                                     )
                                                              )
                                                         []
                                                         ))
              ]
          (if execute?
            (gdriver/runTransactions constraintDropQueries
                                     dataEditQueries
                                     constraintCreateQueries)
            {:constraintDropQueries constraintDropQueries
             :dataEditQueries dataEditQueries
             :constraintCreateQueries constraintCreateQueries}
            )
          )
        (throw (Exception. "Use `:forceMigrate?` true explicitly to make functional changes to the class and it's instances automatically."))
        ))
    )
  )

(defn classExists?
  "Determine if unique class exists.
  :className string (Mandatory)
  :classType string"
  [& {:keys [className
             classType]
      :or {classType nil}}]
  {:pre [(contains? #{nil "NODE" "RELATION"} classType)]}
  (let [parameters (if (nil? classType)
                     {"className" className}
                     {"className" className
                      "classType" classType})]
    (= 1 (first (getNodes :count? true
                          :labels ["Class"]
                          :parameters parameters)))))

(defn getClasses
  "Retrieve all classes"
  [& {:keys [count?]
      :or {count? false}}]
  (getNodes :labels ["Class"]
            :parameters {}
            :count? count?))

(defn deleteClass
  "Deletes a class.
  :className string (Mandatory)
  :forceMigrate? should be true if instances, constraints, etc  are to be deleted as well
  :schemaQueryAggregator and :dataQueryAggregator should both be names of existing queryAggregators,
  either both nil or both strings. Make sure you execute both of these."
  [& {:keys [className
             forceMigrate?
             execute?
             schemaQueryAggregator
             dataQueryAggregator]
      :or {forceMigrate? false
           execute? true
           schemaQueryAggregator nil
           dataQueryAggregator nil}}]
  {:pre [(string? className)]}
  (let [classInstancesCount (first (getClassInstances :className className
                                                      :parameters {}
                                                      :count? true))
        classDelQueries (deleteDetachNodes :labels ["Class"]
                                           :parameters {"className" className}
                                           :execute? false)
        classInstDelQueries (deleteDetachNodes :labels [className]
                                               :execute? false)
        constraintDropQueries (exemptClassNeoConstraints :className className
                                                         :execute? false)
        constraintCreateQueries []
        dataEditQueries (reduceQueryColl [classInstDelQueries classDelQueries])]
    (if (or (= 0 classInstancesCount) forceMigrate?)
      (if execute?
        (gdriver/runTransactions constraintDropQueries
                                 dataEditQueries)
        (if (and schemaQueryAggregator dataQueryAggregator)
          (do (queryAggregator/addQueries :qaName schemaQueryAggregator :queries constraintDropQueries)
              (queryAggregator/addQueries :qaName dataQueryAggregator :queries dataEditQueries))
          {:constraintCreateQueries constraintCreateQueries
           :constraintDropQueries constraintDropQueries
           :dataEditQueries dataEditQueries}))
      (throw (Exception. "Use `:forceMigrate?` true explicitly to make functional changes to the class and it's instances automatically.")))))

(defn defineInitialConstraints
  "Creates Initial constraints"
  [& {:keys [execute?]
      :or {execute? true}}
   ]
  (let [builtQueries
        [(createNCConstraints :execute? false)
         (createATConstraints :execute? false)
         (createCATConstraints :execute? false)
         (createClassConstraints :execute? false)
         (createCFConstraints :execute? false)
         (createCCATConstraints :execute? false)
         (createVRATConstraints :execute? false)
         (createAllNeoConstraints :execute? false)]]
    (if
        execute?
      (apply gdriver/runTransactions builtQueries)
      builtQueries
      )
    )
  )

(defn validatePropertyMaps
  "Validates propertyMaps for a class with className.
  Assumes class with given className exists.
  Returns list of errors
  :className string (Mandatory)
  :propertyMapList should be a list of property maps"
  [& {:keys [className
             propertyMapList]
      :or {propertyMapList []}
      }
   ]
  {:pre [(string? className)
         (coll? propertyMapList)
         (every? map? propertyMapList)]}
  (let [classAttributeTypes (getClassAttributeTypes :className className)
        classATValueRestrictions (reduce (fn
                                           [fullMap classAttributeType]
                                           (let [atname ((into {} classAttributeType) "_name")
                                                 atValueRestrictions (first ((getATValueRestrictions :atName atname) :results))]
                                             (if
                                                 (= 0 (count atValueRestrictions))
                                               (assoc fullMap atname nil)
                                               (assoc fullMap atname (map #(merge (into {} ((% "cf") :properties))
                                                                                  (into {} ((% "vr") :properties)))
                                                                          atValueRestrictions)
                                                      )
                                               )
                                             )
                                           ) {} classAttributeTypes)
        classCustomConstraints (map #(let [customConstraint (merge
                                                             (into {} ((% "cf") :properties))
                                                             (into {} ((% "ccat") :properties))
                                                             )
                                           ]
                                       (assoc customConstraint "atList" (into [] (customConstraint "atList")))
                                       ) (first ((getClassCustomConstraints :className className) :results)))]
    (reduce
     (fn [errors propertyMap]
       (reduce
        (fn [x y]
          (let [property (y 0) datatype (.getName (type (y 1)))]
            (concat (if (not= 1 (count (filter #(= (select-keys % ["_name" "_datatype"])
                                                   { "_name" property
                                                    "_datatype" datatype}
                                                   )
                                               classAttributeTypes)
                                       )
                              )
                      (conj x
                            (str "Unique AttributeType : (" property "," datatype ") not found for " className " in propertyMap -->" propertyMap)
                            )
                      x
                      )
                    (reduce (fn [vrerrors atvr]
                              (if (nil? atvr)
                                vrerrors
                                (let [atvrEr (gcust/checkCustomFunction :fnName (atvr "fnName")
                                                                        :fnString (atvr "fnString")
                                                                        :fnIntegrity (atvr "fnIntegrity")
                                                                        :argumentListX [(y 1)]
                                                                        :constraintValue (atvr "constraintValue"))]
                                  (if
                                      (= true atvrEr)
                                    vrerrors
                                    (conj vrerrors atvrEr)
                                    )
                                  )
                                )
                              ) [] (classATValueRestrictions property))
                    )
            )
          )
        (concat
         errors
         (if
             (>
              (count (keys propertyMap))
              (count classAttributeTypes)
              )
           [(str "No of properties ("
                 (count (keys propertyMap))
                 ") > No of associated AttributeTypes ("
                 (count classAttributeTypes)
                 ") in " className " : " propertyMap)]
           [])
         (reduce (fn [ccerrors ccc]
                   (let [cccEr (gcust/checkCustomFunction :fnName (ccc "fnName")
                                                          :fnString (ccc "fnString")
                                                          :fnIntegrity (ccc "fnIntegrity")
                                                          :argumentListX (seq (map #(propertyMap %) (ccc "atList")))
                                                          :constraintValue (ccc "constraintValue")
                                                          )
                         ]
                     (if
                         (= true cccEr)
                       ccerrors
                       (conj ccerrors cccEr)))) [] classCustomConstraints)
         )
        (seq propertyMap)))
     []
     propertyMapList))
  )

(defn validateClassInstances
  "Validate instances of a class
  :className string (Mandatory)
  :classType string (Mandatory)
  :instList list of maps"
  [& {:keys [className
             classType
             instList]
      :or {instList []}}]
  {:pre [(string? className)
         (string? classType)
         (contains? #{"NODE"
                      "RELATION"}
                    classType)
         (coll? instList)
         (every? map? instList)]}
  (let [fetchedClass (getNodes :labels ["Class"]
                               :parameters {"className" className
                                            "classType" classType}
                               )]
    (if
        (not= 1 (count fetchedClass))
      (throw (Exception. (str "Unique " classType " Class " className " doesn't exist")))
      )
    (if;;MARK remove into {} later
        ((into {} ((first fetchedClass) :properties)) "isAbstract")
      (throw (Exception. (str className " is Abstract")))
      )
    )
  (if (not (empty? instList))
    (let [propertyErrors (validatePropertyMaps :className className
                                               :propertyMapList instList
                                               )
          ]
      (if (not= 0 (count propertyErrors))
        (throw (Exception. (str (seq propertyErrors))))
        )
      )
    )
  )

(defn createNodeClassInstances
  "Creates nodes , as an instance of a Class with classType:NODE.
  :className string (Mandatory)
  :nodeList should be a collection of maps with node properties(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             nodeList
             execute?
             queryAggregator]
      :or {execute? true
           nodeList []
           queryAggregator nil}
      }
   ]
  {:pre [(string? className)
         (coll? nodeList)
         (every? map? nodeList)
         ]
   }
  (validateClassInstances :className className
                          :classType "NODE"
                          :instList nodeList)
  (let [builtQueries (map #(createNewNode :labels [className]
                                          :parameters % 
                                          :execute? false
                                          )
                          nodeList
                          )
        ]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries)))
  )

(defn createRelationClassInstances
  "Creates a relation between two nodes, as an instance of a class with classType:RELATION.
  :className : relation className(Mandatory)
  :relList : list of maps with the following keys(Mandatory)
  -'fromClassName' className of 'out' label.(Mandatory)
  -'fromPropertyMap' a property map that matches one or more 'out' nodes.
  -'propertyMap' relation propertyMap.
  -'toClassName' className of 'in' label.(Mandatory)
  -'toPropertyMap' a property map that matches one or more 'in' nodes.
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             relList
             execute?
             queryAggregator]
      :or {execute? true
           relList []
           queryAggregator nil}
      }
   ]
  {:pre [(string? className)
         (every? string? (map #(% "fromClassName") relList))
         (every? string? (map #(% "toClassName") relList))
         (coll? relList)
         (every? map? relList)]}
  ;;MARK remove into {}
  (let [relApplicableNTs (getRelApplicableNTs :className className)
        relSourceNTs (into #{}
                           (map #((into {} ((% "nt") :properties)) "className")
                                (first relApplicableNTs)
                                )
                           )
        relTargetNTs (into #{} (map #((into {} ((% "nt") :properties)) "className")
                                    (last relApplicableNTs)
                                    )
                           )
        ]
    (doall (map (fn [relation]
                  (if
                      (not (contains? relSourceNTs (relation "fromClassName")))
                    (throw (Exception. (str (relation "fromClassName")
                                            " is not an ApplicableSourceNT for "className)
                                       )
                           )
                    )
                  (if
                      (not (contains? relTargetNTs (relation "toClassName")))
                    (throw (Exception. (str (relation "toClassName")
                                            " is not an ApplicableTargetNT for "className)
                                       )
                           )
                    )
                  )
                relList)
           )
    )
  (validateClassInstances :className className
                          :classType "RELATION"
                          :instList (map #(initmap (% "propertyMap")) relList)
                          )
  (let [builtQueries (map #(createRelation
                            :fromNodeLabels [(% "fromClassName")]
                            :fromNodeParameters (initmap (% "fromPropertyMap"))
                            :relationshipType className
                            :relationshipParameters (initmap (% "propertyMap"))
                            :toNodeLabels [(% "toClassName")]
                            :toNodeParameters (initmap (% "toPropertyMap"))
                            :execute? false
                            :unique? true) relList)]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      (if queryAggregator
        (queryAggregator/addQueries :qaName queryAggregator :queries builtQueries)
        builtQueries))))

(defn editNodeClassInstances
  "Edit Instances of a Node Class.
  :className string (Mandatory)
  :parameters should be a map with properties to match class instances with.
  :changeMap should be a map with parameters to be changed.(Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             parameters
             changeMap
             execute?
             queryAggregator]
      :or {parameters {}
           changeMap {}
           execute? true
           queryAggregator nil}}]
  (validateClassInstances :className className
                          :classType "NODE"
                          :instList [changeMap])
  (editNodeProperties :labels [className]
                      :parameters parameters
                      :changeMap changeMap
                      :execute? execute?
                      :queryAggregator queryAggregator))

(defn editRelationClassInstances
  "Edit Instances of a Relation Class.
  :className string (Mandatory)
  :relationshipParameters should be a map to match relations by.
  :newRelationshipParameters should be a map of new properties for the relation
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             relationshipParameters
             newRelationshipParameters
             fromNodeParameters
             toNodeParameters
             fromNodeLabels
             toNodeLabels
             execute?
             queryAggregator]
      :or {relationshipParameters {}
           newRelationshipParameters {}
           toNodeParameters {}
           fromNodeParameters {}
           toNodeLabels []
           fromNodeLabels []
           execute? true
           queryAggregator nil}
      :as keyArgs}]
  (validateClassInstances :className className
                          :classType "RELATION"
                          :instList [newRelationshipParameters])
  (apply editRelation (prepMapAsArg (dissoc (assoc keyArgs :relationshipType className) :className))))

(defn deleteNodeClassInstances
  "Delete Instances of a Node Class.
  :className string (Mandatory)
  :parameters should be a map with properties to match class instances with.
  You must use :detach? explicitly if the instances have relations
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             parameters
             detach?
             execute?
             queryAggregator]
      :or {parameters {}
           detach false
           execute? true
           queryAggregator nil}}]
  {:pre [(string? className)
         (classExists? :className className
                       :classType "NODE")]}
  (if detach?
    (deleteDetachNodes :labels [className]
                       :parameters parameters
                       :execute? execute?
                       :queryAggregator queryAggregator)
    (deleteNodes :labels [className]
                 :parameters parameters
                 :execute? execute?
                 :queryAggregator queryAggregator)))

(defn deleteRelationClassInstances
  "Delete Instances(Relations) of a relation class
  :className string (Mandatory)
  :queryAggregator should be a string reperesenting name of an existing queryAggregator
  To use queryAggregator you should also set :execute? to false"
  [& {:keys [className
             relationshipParameters
             fromNodeParameters
             toNodeParameters
             fromNodeLabels
             toNodeLabels
             execute?
             queryAggregator]
      :or {relationshipParameters {}
           toNodeParameters {}
           fromNodeParameters {}
           toNodeLabels []
           fromNodeLabels []
           execute? true
           queryAggregator nil}
      :as keyArgs}]
  {:pre [(string? className)
         (classExists? :className className
                       :classType "RELATION")]}
  (apply deleteRelation (prepMapAsArg (dissoc (assoc keyArgs :relationshipType className) :className))))
