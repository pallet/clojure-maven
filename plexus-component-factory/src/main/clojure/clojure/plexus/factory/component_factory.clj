(ns clojure.plexus.factory.component-factory
  "ComponentFactory for Plexus components implemented in clojure."
  (:require
   [clojure.string :as string]))

(defn instantiate
  "Instantiate the given class name"
  [name]
  (let [name (if (.startsWith name "class ")
               (.substring name 6)
               name)
        i (.lastIndexOf name ".")
        the-ns (-> name (.substring 0 i) (string/replace #"_" "-"))
        the-class (-> name (.substring (inc i)) (string/replace #"_" "-"))]
    (require (symbol the-ns))
    (when-let [factory (find-var (symbol the-ns (str "make-" the-class)))]
      (factory))))
