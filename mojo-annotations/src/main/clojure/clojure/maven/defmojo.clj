(ns clojure.maven.defmojo
  (:import
   java.io.File
   [clojure.maven.annotations
    Goal RequiresDependencyResolution Phase Parameter Component]
   org.apache.maven.plugin.ContextEnabled
   org.apache.maven.plugin.Mojo
   org.apache.maven.plugin.MojoExecutionException))


(defmacro defmojo [mojoType annotations-map & rest]

  (let [keyword-to-java {:goal 'clojure.maven.annotations.Goal,
                         :requires-dependency-resolution 'clojure.maven.annotations.RequiresDependencyResolution,
                         :phase 'clojure.maven.annotations.Phase}

        mojo-annotations (into {} (map (fn [[k v]] [(k keyword-to-java) v]) annotations-map))
        [parameters body]   (vec (partition-by (fn [[p-name p-options]]
                                                (and (symbol? p-name) (map? p-options))) (partition-all 2 rest)))
        ]

    `(do
       (deftype
          ;; Mojo annotations
          ~(with-meta mojoType mojo-annotations)
           


          ;; Mojo parameters
          ~(apply vector
                  (concat (map (fn [[p-symbol p-options]] (with-meta p-symbol (hash-map 'clojure.maven.annotations.Parameter p-options )) ) parameters)
                          ;; pre-defined parameters
                          `( ~(with-meta 'log {:volatile-mutable true})
                            ~'plugin-context
                            )))

        ;; Mojo predefined methods
        Mojo
        ~'(setLog [_ logger] (set! log logger))
        ~'(getLog [_] log)

        ;; Mojo's suplied methods (TODO: validate it includes an "execute" method
        ~@(mapcat identity body)

        ;; Plugin-Context handling
        ContextEnabled
        ~'(setPluginContext [_ context] (reset! plugin-context context))
        ~'(getPluginContext [_] @plugin-context)
        )


        (defn ~(symbol (str "make-" mojoType))
         "Function to provide a no argument constructor"
         []
         (~(symbol (str mojoType ".")) ~@(repeat (count parameters) nil) nil (atom nil)))

       )
    ))




;; (comment
;;   Example of use:

  
;;   (defmojo MyMojo

;;     {:goal "simple"
;;      :requires-dependency-resolution "test"
;;      :phase "validate" }

;;     ;; Mojo parameters (note: 'log' and 'plugin-context' are already defined for you)
;;     base-directory   {:expression "${basedir}" :required true :readonly true}
;;     project          {:expression "${project}" :required true :readonly true}
;;     output-directory {:defaultValue "${project.build.outputDirectory}" :required true}
;;     xxx              {}

;;     ;; Do it!
;;     (execute [_]
;;              ;; 
;;              (.info log (str "* Infering project version *" output-directory))
;;              (.info log (str "* project.version = " (.getVersion project))))))

