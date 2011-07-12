(ns clojure.plexus.compiler.ClojureCompiler
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
   [clojure.string :as string]
   classlojure))

(defn absolute-filename [filename]
  (.getPath (java.io.File. filename)))

(defn filename-to-url-string
  [filename]
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

(defn file-to-namespace
  "Convert a filename to a namespace name"
  [locations filename]
  (-> (reduce #(string/replace %1 %2 "") filename locations)
      (string/replace ".clj" "")
      (string/replace "_" "-")
      (string/replace #"^/" "")
      (string/replace "/" ".")))

(defn call-method
  "Calls a private or protected method.

   params is a vector of classes which correspond to the arguments to
   the method e

   obj is nil for static methods, the instance object otherwise.

   The method-name is given a symbol or a keyword (something Named)."
  [klass method-name params obj & args]
  (-> klass (.getDeclaredMethod (name method-name)
                                (into-array Class params))
      (doto (.setAccessible true))
      (.invoke obj (into-array Object args))))

(defn -init []
  [[CompilerOutputStyle/ONE_OUTPUT_FILE_PER_INPUT_FILE ".clj" ".class" nil]
   nil])

(defn -compile
  [this ^CompilerConfiguration config]
  (let [output-dir (java.io.File. (.getOutputLocation config))
        source-locations (seq (.getSourceLocations config))
        classpath-elements (seq (.getClasspathEntries config))
        source-files (call-method
                      AbstractCompiler "getSourceFiles" [CompilerConfiguration]
                      nil config)
        logger (.getLogger ^AbstractCompiler this)]
    (when-not (.exists output-dir)
      (.mkdirs output-dir))
    (when (and logger (.isInfoEnabled logger))
      (.info logger
             (format
              "Compiling %s source file%s to %s"
              (count source-files)
              (if (= 1 (count source-files)) "" "s")
              (.getAbsolutePath output-dir))))
    (if (.isFork config)
      (throw
       (CompilerException.
        "The clojure compiler doesn't yet support the fork execution model"))
      (let [cl (classloader-for (concat source-locations classpath-elements))]
        (->>
         (for [source source-files
               :let [file-ns (file-to-namespace source-locations source)]]
           (try
             (.debug logger (format "Compiling %s" file-ns))
             (classlojure/eval-in
              cl
              `(binding [*compile-path* ~(.getOutputLocation config)]
                 (compile '~(symbol file-ns))))
             nil
             (catch Exception e
               (let [msg (.getMessage e)
                     comps (when msg (re-find #".*\(.*:([0-9]+)\)$" msg))
                     line (if-let [line-str (second comps)]
                            (Integer/parseInt line-str)
                            0)]
                 (CompilerError. source true line 0 line 0 msg)))))
         (filter identity)
         (java.util.ArrayList.))))))

(defn -createCommandLine
  [this ^CompilerConfiguration config]
  )
