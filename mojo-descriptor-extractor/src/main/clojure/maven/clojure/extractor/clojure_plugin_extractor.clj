(ns maven.clojure.extractor.clojure-plugin-extractor
  "Equivalent of JavaMojoDescriptorExtractor in
   org.apache.maven.tools.plugin.extractor.java, but using java annotations
   rather than javadoc annotations."
  (:require
   [clojure.contrib.find-namespaces :as find-ns]
   [clojure.string :as string]
   [clojure.pprint :as pprint]
   classlojure)
  (:import
   [maven.clojure.annotations Goal RequiresDependencyResolution]
   [org.apache.maven.plugin.descriptor
    MojoDescriptor
    Parameter
    InvalidPluginDescriptorException
    Requirement]
   [java.lang.annotation
    Retention RetentionPolicy Target Inherited ElementType]))


(defn file-to-ns-sym
  "Convert a file name to the corresponding namespace name"
  [^String file]
  (symbol (-> file
              (.replaceFirst ".clj$" "")
              (.replaceAll "/" ".")
              (.replaceAll "_" "-"))))

(defn test-var [v]
  (println "Testing" v
           (type v)
           (instance? org.apache.maven.plugin.Mojo v)
           (isa? v org.apache.maven.plugin.Mojo))
  (flush)
  v)

(defn filename-to-url-string
  [filename]
  (str (.toURL (java.io.File. filename))))

(defn blank-as-nil [s] (when-not (string/blank? s) s))

;;; Mojo deftypes to data maps of annotation data

(defn to-ns
  "Converts found mojo's into literal descriptor maps"
  [base-path cl ^java.io.File file]
  (when (and (.isFile file) (.endsWith (.getPath file) ".clj"))
    (let [path (.getPath file)
          path (.substring path (inc (count base-path)) (count path))
          path (if (.startsWith path "main/clojure/")
                 (.substring path  (count "main/clojure/") (count path))
                 path)
          try-ns (file-to-ns-sym path)]
      (classlojure/eval-in
       cl
       `(let [field-params#
              (fn [^java.lang.reflect.Field field#]
                (if-let [^maven.clojure.annotations.Parameter parameter#
                         (.getAnnotation
                          field#
                          maven.clojure.annotations.Parameter)]
                  {:name (.getName field#)
                   :type (.getName (.getType field#))
                   :parameter {:alias (.alias parameter#)
                               :expression (.expression parameter#)
                               :description (.description parameter#)
                               :editable (not (.readonly parameter#))
                               :required (.required parameter#)
                               :defaultValue (.defaultValue parameter#)}}
                  (if-let [^maven.clojure.annotations.Component component#
                           (.getAnnotation
                            field#
                            maven.clojure.annotations.Component)]
                    {:name (.getName field#)
                     :type (.getName (.getType field#))
                     :component {:role (.role component#)
                                 :roleHint (.roleHint component#)}})))
              mojo-descriptor#
              (fn [^java.lang.Class v#]
                (let [^Goal goal# (.getAnnotation v# Goal)
                      ^RequiresDependencyResolution resolution#
                      (.getAnnotation v# RequiresDependencyResolution)]
                  (when-not goal#
                    (throw (IllegalArgumentException.
                            (str "No Goal annotation for " v#))))
                  {:goal (.value goal#)
                   :resolution (when resolution# (.value resolution#))
                   :implementation (.getName v#)
                   :fields (->>
                            (.getFields v#)
                            (map #(field-params# %) )
                            (filter identity))}))
              test-var#
              (fn [v#]
                (println "Testing" v#
                         (type v#)
                         (instance? org.apache.maven.plugin.Mojo v#)
                         (isa? v# org.apache.maven.plugin.Mojo))
                (flush)
                v#)]
          (require '~try-ns)
          (->>
           (ns-map (find-ns '~try-ns))
           (map second)
           (filter
            #(and (not (var? %))
                  (not (= org.apache.maven.plugin.Mojo %))
                  (isa? % org.apache.maven.plugin.Mojo)))
           (map #(mojo-descriptor# %))
           doall))))))

(defn process-source-tree
  [cl ^String source]
  (let [dir (java.io.File. source)]
    (->>
     (mapcat #(to-ns (.getPath dir) cl %) (file-seq dir))
     (filter identity))))

(defn sources
  [project]
  (filter
   identity
   (mapcat
    #(vector
      %
      (when (.endsWith % "/java")
        (string/replace %"/java" "/clojure")))
    (.getCompileSourceRoots project))))

(defn field-parameter
  [field]
  (let [p (doto (Parameter.)
            (.setName (:name field))
            (.setType (:type field)))]
    (if-let [component (:component field)]
      (doto p
        (.setRequirement
         (Requirement.
          (-> field :component :role)
          (when-let [hint (-> field :component :roleHint)]
            (when-not (string/blank? hint)
              hint))))
        (.setEditable false)
        (.setType (-> field :component :role)))
      (when-let [parameter (:parameter field)]
        (doto p
          (.setAlias (blank-as-nil (:alias parameter)))
          (.setExpression (blank-as-nil (:expression parameter)))
          (.setDescription (blank-as-nil (:description parameter)))
          (.setEditable (not (:readonly parameter)))
          (.setRequired (boolean (:required parameter)))
          (.setDefaultValue (:defaultValue parameter)))))
    p))

(defn mojo-descriptor
  [plugin-descriptor {:keys [implementation goal resolution fields] :as m}]
  (when-not goal
    (throw
     (IllegalArgumentException.
      (str "No Goal annotation for " implementation))))
  (let [descriptor (doto (MojoDescriptor.)
                     (.setPluginDescriptor plugin-descriptor)
                     (.setLanguage "clojure")
                     (.setImplementation implementation)
                     (.setGoal goal)
                     (.setThreadSafe false))]
    (when resolution
      (.setDependencyResolutionRequired descriptor resolution))
    (doseq [field fields]
      (.addParameter descriptor (field-parameter field)))
    descriptor))

;;; entry point

(defn plugin-classes
  [plugin-descriptor project]
  (let [sources (distinct (sources project))
        cl (apply
            classlojure/classlojure
            (map
             filename-to-url-string
             (concat sources (.getCompileClasspathElements project))))]
    (java.util.ArrayList.
     (->>
      sources
      (mapcat #(process-source-tree cl %))
      (map #(mojo-descriptor plugin-descriptor %))))))