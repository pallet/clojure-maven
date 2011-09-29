(ns clojure.maven.extractor.clojure-mojo-extractor
  "Equivalent of JavaMojoDescriptorExtractor in
   org.apache.maven.tools.plugin.extractor.java, but using java annotations
   rather than javadoc annotations."
  (:require
   [clojure.contrib.find-namespaces :as find-ns]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.pprint :as pprint]
   classlojure)
  (:import
   [clojure.maven.annotations Goal Phase RequiresDependencyResolution]
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
  [base-path output-path cl ^java.io.File file]
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
                (if-let [^clojure.maven.annotations.Parameter parameter#
                         (.getAnnotation
                          field#
                          clojure.maven.annotations.Parameter)]
                  {:name (.getName field#)
                   :type (.getName (.getType field#))
                   :parameter {:alias (.alias parameter#)
                               :expression (.expression parameter#)
                               :description (.description parameter#)
                               :editable (not (.readonly parameter#))
                               :required (.required parameter#)
                               :defaultValue (.defaultValue parameter#)
                               :typename (.typename parameter#)}}
                  (if-let [^clojure.maven.annotations.Component component#
                           (.getAnnotation
                            field#
                            clojure.maven.annotations.Component)]
                    {:name (.getName field#)
                     :type (.getName (.getType field#))
                     :component {:role (.role component#)
                                 :roleHint (.roleHint component#)}})))
              mojo-descriptor#
              (fn [^java.lang.Class v#]
                (let [^Goal goal# (.getAnnotation v# Goal)
                      ^Phase phase# (.getAnnotation v# Phase)
                      ^RequiresDependencyResolution resolution#
                      (.getAnnotation v# RequiresDependencyResolution)]
                  (when-not goal#
                    (throw (IllegalArgumentException.
                            (str "No Goal annotation for " v#))))
                  {:goal (when goal# (.value goal#))
                   :phase (when phase# (.value phase#))
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
          (binding [*compile-path* ~output-path]
            (compile '~try-ns))
          (require '~try-ns)
          (->>
           (ns-map '~try-ns)
           (map second)
           (filter
            #(and (not (var? %))
                  (not (= org.apache.maven.plugin.Mojo %))
                  (isa? % org.apache.maven.plugin.Mojo)))
           (map #(mojo-descriptor# %))
           doall))))))

(defn process-source-tree
  [output-path cl ^String source]
  (let [dir (java.io.File. source)]
    (->>
     (mapcat #(to-ns (.getPath dir) output-path cl %) (file-seq dir))
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
            (.setType (or (-> field :parameter :typename) (:type field))))]
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
          (.setImplementation (blank-as-nil (:typename parameter)))
          (.setAlias (blank-as-nil (:alias parameter)))
          (.setExpression (blank-as-nil (:expression parameter)))
          (.setDescription (blank-as-nil (:description parameter)))
          (.setEditable (not (:readonly parameter)))
          (.setRequired (boolean (:required parameter)))
          (.setDefaultValue (:defaultValue parameter)))))
    p))

(defn mojo-descriptor
  [plugin-descriptor {:keys [implementation goal resolution fields phase]}]
  (when-not goal
    (throw
     (IllegalArgumentException.
      (str "No Goal annotation for " implementation))))
  (let [descriptor (doto (MojoDescriptor.)
                     (.setPluginDescriptor plugin-descriptor)
                     (.setLanguage "clojure")
                     (.setImplementation implementation)
                     (.setGoal goal)
                     (.setPhase (blank-as-nil phase))
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
        output-path (io/file
                     (.. project getBuild getOutputDirectory)
                     ".." "plugin-classes" )
        cl (apply
            classlojure/classlojure
            (map
             filename-to-url-string
             (concat sources (.getCompileClasspathElements project))))]
    (when-not (.exists output-path)
      (.mkdirs output-path))
    (java.util.ArrayList.
     (->>
      sources
      (mapcat
       #(process-source-tree (.getAbsolutePath output-path) cl %))
      (map #(mojo-descriptor plugin-descriptor %))))))
