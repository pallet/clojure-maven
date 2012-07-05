# Release Notes

Current release is 0.3.3.

## 0.3.3

- Fix line reporting in clojure compiler plugin

## 0.3.2

### Features

- New defmojo macro. Syntactic sugar for defining mojos in clojure

### Fixes

- Update to recent classlojure and separate ClojureCompiler implementation
  The separation of ClojureCompiler implementation allows us to control the
  transitive compilation of it's dependencies.

- Add .ritz-exception-filters to .gitignore

- Add release scripts

- Update to use zi for building and running tests

- Remove superfluous compile

- Ensure clojure version is consistent across sub-projects

- Add new clojure-maven-mojo artifact for defmojo

- Fix typo on clojars repository URL

- Remove unused clojure.contrib dependency

## 0.3.1

- Write plugin extractor generated class files to target/plugin-classes
  The class files were being written to the output directory and were being
  included in mojo jar packages.

## 0.3.0

- Add a ClojureScript compiler component

## 0.2.0

- Compile clojure before looking for Mojos
  Enables use of gen-class to build mojos.

- Add plexus clojure compiler component

- Add Phase annotation for mojos

- Remove check on classloader of generated component


## 0.1.0

- Initial release with basic functionality (no support for lists in parameters)
