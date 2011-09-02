(ns clojure.plexus.compiler.ClojureScriptCompiler
  (:gen-class
   :extends org.codehaus.plexus.compiler.AbstractCompiler
   :init init
   :constructors {[] [org.codehaus.plexus.compiler.CompilerOutputStyle
                      String String String]})
  (:import
   org.codehaus.plexus.compiler.AbstractCompiler
   org.codehaus.plexus.compiler.CompilerConfiguration
   org.codehaus.plexus.compiler.CompilerError
   org.codehaus.plexus.compiler.CompilerException
   org.codehaus.plexus.compiler.CompilerOutputStyle)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.stacktrace :as stacktrace]
   classlojure))

(defn absolute-filename [^String filename]
  (.getPath (java.io.File. filename)))

(defn filename-to-url-string
  [^String filename]
  (str (.toURL (java.io.File. filename))))

(defn classloader-for
  "Classloader for the specified files"
  [files]
  (apply
   classlojure/classlojure
   (->>
    files
    (map absolute-filename)
    (map filename-to-url-string))))

(defn loader-classpath
  "Returns a sequence of File paths from a classloader."
  [loader]
  (when (instance? java.net.URLClassLoader loader)
    (map
     #(java.io.File. (.getPath ^java.net.URL %))
     (.getURLs ^java.net.URLClassLoader loader))))

(defn classpath
  "Returns a sequence of File objects of the elements on the classpath."
  []
  (distinct
   (mapcat
    loader-classpath
    (take-while
     identity
     (iterate #(.getParent ^ClassLoader %) (clojure.lang.RT/baseLoader))))))

(def target-file "cljsapp.js")

(defn -init []
  [[CompilerOutputStyle/ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES
    ".cljs" ".js" target-file]
   nil])

(defn -compile
  [^AbstractCompiler this ^CompilerConfiguration config]
  (let [output-dir (java.io.File. (.getOutputLocation config))
        source-locations (seq (.getSourceLocations config))
        classpath-elements (seq (.getClasspathEntries config))
        ^org.codehaus.plexus.logging.Logger logger (.getLogger this)]
    (when-not (.exists output-dir)
      (.mkdirs output-dir))
    (when (and logger (.isInfoEnabled logger))
      (.info logger
             (format
              "Compiling %s to %s"
              (first source-locations)
              (.getAbsolutePath output-dir))))

    (if (.isFork config)
      (throw
       (CompilerException.
        "The clojurescript compiler doesn't yet support forked execution"))
      (let [elements (concat
                      source-locations classpath-elements
                      (map #(.getAbsolutePath ^java.io.File %) (classpath)))
            _ (.debug logger (format "Elements %s" (vec elements)))
            cl (classloader-for elements)]
        (try
          (.debug logger (format "Compiling %s" (first source-locations)))
          (classlojure/eval-in
           cl
           `(do
              (binding [*warn-on-reflection* nil]
                (require '~'cljs.closure)
                (if-let [build# (ns-resolve '~'cljs.closure '~'build)]
                  (build#
                   ~(first source-locations)
                   ~(merge
                     (zipmap
                      (map keyword (keys (.getCustomCompilerArguments config)))
                      (map str (vals (.getCustomCompilerArguments config))))
                     {:optimizations (if (.isDebug config)
                                       nil
                                       (if (.isOptimize config)
                                         :advanced
                                         :simple))
                      :pretty-print (.isVerbose config)
                      :target (keyword (.getTargetVersion config))
                      :output-dir (.getAbsolutePath output-dir)
                      :output-to (.getAbsolutePath
                                  (io/file
                                   output-dir
                                   (or
                                    (.getOutputFileName config)
                                    target-file)))}))
                  (throw (Exception. "Build method not found"))))))
          (java.util.ArrayList.)
          (catch Exception e
            (.debug logger (format "Exception cause - %s"
                                   (with-out-str
                                     (stacktrace/print-stack-trace
                                      (stacktrace/root-cause e)))))
            (let [^Throwable e (stacktrace/root-cause e)
                  msg (.getMessage e)
                  source "tbd"
                  comps (when msg (re-find #".*\(.*:([0-9]+)\)$" msg))
                  line (if-let [line-str (second comps)]
                         (Integer/parseInt line-str)
                         0)]
              (java.util.ArrayList.
               [(CompilerError. source true line 0 line 0 msg)]))))))))

(defn -createCommandLine
  [this ^CompilerConfiguration config]
  )
