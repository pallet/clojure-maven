(ns clojure.maven.mojo.defmojo-test
  (:use
   clojure.test
   clojure.maven.mojo.defmojo
   clojure.walk)
  (:require
   [clojure.string :as str]
   [clojure.maven.mojo.log :as log])
  (:import
   org.apache.maven.plugin.ContextEnabled
   org.apache.maven.plugin.Mojo
   org.apache.maven.plugin.MojoExecutionException))



(deftest defmacro-minimal

  (is (=
       (macroexpand-1
        '(defmojo
             MyFirstMojo {} [] (log/info "Hello World!")))

       '(do
          (clojure.core/deftype MyFirstMojo
            [ ^{:volatile-mutable true} log
              plugin-context ]

            org.apache.maven.plugin.Mojo
            (setLog [_ logger] (set! log logger))
            (getLog [_] log)
            (execute [this] (clojure.maven.mojo.log/with-log log
                              (log/info "Hello World!")))

            org.apache.maven.plugin.ContextEnabled
            (setPluginContext [_ context] (reset! plugin-context context))
            (getPluginContext [_] (clojure.core/deref plugin-context)))

          (clojure.core/defn make-MyFirstMojo
            "Function to provide a no argument constructor" []
            (MyFirstMojo. nil (clojure.core/atom nil)))))))


(deftest defmacro-basic

  ;; Note: By definition '=' doesn't compare metadata.
  ;; Since defmojo generates quite a bit of metadata, we are not really testing
  ;; all there is to it here :-(
  (is (=
       (macroexpand-1
        '(defmojo
             MyFirstMojo {:goal "hello-world"}
             [
              project {:expression "${project}"}
              basedir {:expression "${basedir}"}
              ]
           (log/info "Hello World!")))

       '(do
          (clojure.core/deftype ^{clojure.maven.annotations.Goal "hello-world"}
            MyFirstMojo
            [^{clojure.maven.annotations.Parameter {:expression "${project}"}}
             project
             ^{clojure.maven.annotations.Parameter {:expression "${basedir}"}}
             basedir
             ^{:volatile-mutable true}
             log
             plugin-context]

            org.apache.maven.plugin.Mojo
            (setLog [_ logger] (set! log logger))
            (getLog [_] log)

            (execute [this] (clojure.maven.mojo.log/with-log log
                              (log/info "Hello World!")))

            org.apache.maven.plugin.ContextEnabled
            (setPluginContext [_ context] (reset! plugin-context context))
            (getPluginContext [_] (clojure.core/deref plugin-context)))

          (clojure.core/defn make-MyFirstMojo
            "Function to provide a no argument constructor" []
            (MyFirstMojo. nil nil nil (clojure.core/atom nil)))))))



(deftest body-missing
  (is (thrown-with-msg? IllegalArgumentException #"Body.*missing"
        (macroexpand-1 '(defmojo Test {} [])))))

(deftest body-multiple-sexps
  (is (thrown-with-msg? IllegalArgumentException #"Body.*do"
        (macroexpand-1 '(defmojo Test {} []
                          (log/info "first")
                          (log/info "second"))))))

(deftest invalid-mojo-annotations
  (is (thrown-with-msg? IllegalArgumentException #"annotation"
        (macroexpand-1 '(defmojo Test
                            non-map
                            []
                          (log/info "hello world!"))))))


(deftest invalid-mojo-annotation
  (is (thrown-with-msg? IllegalArgumentException #"annotation"
        (macroexpand-1 '(defmojo Test
                            {:goalie "test"}
                            []
                          (log/info "hello world!"))))))

(deftest invalid-params-vector
  (is (thrown-with-msg? IllegalArgumentException #"vector.*param"
        (macroexpand-1 '(defmojo
                            Test {}
                            non-vector
                          (log/info "hello world!"))))))

(deftest invalid-param-pair
  (is (thrown-with-msg? IllegalArgumentException #"parameter"
        (macroexpand-1 '(defmojo
                            Test {}
                            [param-name]
                          (log/info "hello world!")))))

  (is (thrown-with-msg? IllegalArgumentException #"parameter"
        (macroexpand-1 '(defmojo
                            Test {}
                            [param-name [:required "true"]]
                          (log/info "hello world!")))))

  (is (thrown-with-msg? IllegalArgumentException #"parameter"
        (macroexpand-1 '(defmojo
                            Test {}
                            [param-name no-map]
                          (log/info "hello world!"))))))

(deftest invalid-param-pair
  (is (thrown-with-msg? IllegalArgumentException #"parameter"
        (macroexpand-1 '(defmojo
                            Test
                            {:goal "test"}
                            [param-name no-map]
                          (log/info "hello world!"))))))
