(ns gnowdb.neo4j.queryAggregator
  (:gen-class)
  (:require [gnowdb.neo4j.gdriver :as gdriver]))

(def ^{:private true} qa-prefix "QA-")

(defn queryAggregator
  [& {:keys [qaName schema?]}]
  (atom {:queryList [] :qaName qaName :schema? schema?}))

(defn concatVar
  [& {:keys [qaName]}]
  {:pre [(string? qaName)]}
  (str qa-prefix qaName))

(defn getQA
  "Get the queryAggregator var from qaName"
  [& {:keys [qaName]
      :or {qaName ""}}]
  (ns-resolve 'gnowdb.neo4j.queryAggregator (symbol (concatVar :qaName qaName))))

(defn qaExists?
  "Returns whether thequeryAggregator exists"
  [& {:keys [qaName]
      :or {qaName ""}}]
  (boolean (getQA :qaName qaName)))

(defn createQA
  "Creates a queryAggregator.
  :qaName should be string. (Mandatory)
  :schema? (boolean) should be true if constraints/indexes are being added.
  Neo4j doesnt allow for schema changes and data changes in
  a single transaction.
  A queryAggregator will also follow the same rule."
  [& {:keys [qaName
             schema?]
      :or {qaName ""}}]
  {:pre [(string? qaName)
         (not (empty? qaName))
         (not (qaExists? :qaName qaName))]}
  (intern 'gnowdb.neo4j.queryAggregator (symbol (concatVar :qaName qaName)) (queryAggregator :qaName qaName :schema? (boolean schema?))))

(defn delQA
  "Deletes a created queryAggregator"
  [& {:keys [qaName]
      :or {qaName ""}}]
  (ns-unmap 'gnowdb.neo4j.queryAggregator (symbol (concatVar :qaName qaName))))

(defn emptyQA
  "Empties the queries in the queryAggregator"
  [& {:keys [qaName]
      :or {qaName ""}}]
  {:pre [(string? qaName)
         (not (empty? qaName))]}
  (reset! (var-get (getQA :qaName qaName)) {:queryList () :qaName qaName}))

(defn addQueries
  "Add queries to queryAggregator.
  :qaName should be String (Mandatory)
  :queries should be a list of maps. Same format as the input for gdriver/runQuery (Not mandatory, but you
   will only waste your time)"
  [& {:keys [qaName queries]
      :or {qaName ""
           queries ()}}]
  {:pre [(string? qaName)
         (qaExists? :qaName qaName)]}
  (let [qa (var-get (getQA :qaName qaName))]
    (if (= (@qa :schema?)
           (boolean (some #(:schema-changed? %) queries)))
      (swap! qa #(update-in %1 [:queryList] (fn [ql] (concat ql %2))) queries)
      (throw (Exception. (str "Schema changes and Data changes cannot be done in the same transaction."))))))

(defn runQueries
  "Runs the queries in the queryAggregator
  :qaName String is mandatory"
  [& {:keys [qaName]
      :or {qaName ""}}]
  {:pre [(string? qaName)
         (not (empty? qaName))]}
  (apply gdriver/runQuery  ((deref (var-get (getQA :qaName qaName))) :queryList)))

(defn flushQueries
  "Runs the queries in the queryAggregator and empties the query aggregator.
  Does not delete the queryAggregator atom.
  :qaName is mandatory"
  [& {:keys [qaName]
      :or {qaName ""}}]
  (let [ret (runQueries :qaName qaName)]
    (swap! (var-get (getQA :qaName qaName)) #(assoc % :queryList []))
    ret))










