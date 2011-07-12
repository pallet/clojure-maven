# clojure-maven

Maven components to allow the use of clojure when writing maven plugins.

## Usage

### clojure plugin

To write a clojure plugin, you create a deftype, implement the Mojo interface,
and create a no-argument constructor function (which must be named
`make-YourType`).

Annotations are used to get values from the pom.

```clojure
    (ns example.simple
      "Simple mojo"
      (:import
       java.io.File
       [clojure.maven.annotations
        Goal RequiresDependencyResolution Parameter Component]
        org.apache.maven.plugin.ContextEnabled
        org.apache.maven.plugin.Mojo
        org.apache.maven.plugin.MojoExecutionException))

    (deftype
        ^{Goal "simple"
          RequiresDependencyResolution "test"}
        SimpleMojo
      [
       ^{Parameter
         {:expression "${basedir}" :required true :readonly true}}
       base-directory

       ^{Parameter
         {:defaultValue "${project.compileClasspathElements}"
          :required true :readonly true :description "Compile classpath"}}
       classpath-elements

       ^{Parameter
         {:defaultValue "${project.testClasspathElements}"
          :required true :readonly true}}
       test-classpath-elements

       ^{Parameter
         {:defaultValue "${project.build.outputDirectory}" :required true}}
       output-directory

       ^{:volatile-mutable true}
       log

       plugin-context
       ]

      Mojo
      (execute [_]
        (.info log sourceDirectories))

      (setLog [_ logger] (set! log logger))
      (getLog [_] log)

      ContextEnabled
      (setPluginContext [_ context] (reset! plugin-context context))
      (getPluginContext [_] @plugin-context))

    (defn make-SimpleMojo
      "Function to provide a no argument constructor"
      []
      (SimpleMojo. nil nil nil nil nil (atom nil)))
```

### pom.xml

To write a plugin in clojure, your pom packaging should be set to `maven-plugin`.

```xml
    <packaging>maven-plugin</packaging>
```

To enable the mojo descriptor extractor, the `maven-plugin` plugin needs to be
configured.

```xml
      <plugin>
        <artifactId>maven-plugin-plugin</artifactId>
        <version>2.6</version>
        <dependencies>
          <dependency>
            <groupId>org.cloudhoist</groupId>
            <artifactId>clojure-maven-mojo-descriptor-extractor</artifactId>
            <version>0.2.0</version>
          </dependency>
        </dependencies>
      </plugin>
```

To write the mojo, you will need to make use of the annotations in
`clojure-maven-mojo-annotations`:

```xml
    <dependency>
      <groupId>org.cloudhoist</groupId>
      <artifactId>clojure-maven-mojo-annotations</artifactId>
      <version>0.2.0</version>
    </dependency>
```

And to be able to use the mojo, you need a dependency on
`clojure-maven-plexus-component-factory`:

```xml
    <dependency>
      <groupId>org.cloudhoist</groupId>
      <artifactId>clojure-maven-plexus-component-factory</artifactId>
      <version>0.2.0</version>
      <scope>runtime</scope>
    </dependency>
```

To be able to find the artifacts, add the sonatype repository.

```xml
    <repository>
      <id>sonatype</id>
      <url>http://oss.sonatype.org/content/repositories/releases</url>
    </repository>
```

## Example

See [zi](https://github.com/pallet/zi).

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)
