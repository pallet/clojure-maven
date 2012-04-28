(ns clojure.plexus.compiler.ClojureCompiler
  (:gen-class
   :extends org.codehaus.plexus.compiler.AbstractCompiler
   :init init
   :constructors {[] [org.codehaus.plexus.compiler.CompilerOutputStyle
                      String String String]}
   :impl-ns clojure.plexus.compiler.impl))
