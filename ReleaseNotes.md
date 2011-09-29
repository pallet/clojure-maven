# Release Notes

Current release is 0.3.1.

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
