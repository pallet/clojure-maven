package maven.clojure.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A plugin parameter. */
@MojoAnnotation
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
@Inherited
public @interface Parameter {
  String description() default "";
  boolean required() default false;
  boolean readonly() default false;
  String deprecated() default "";
  String alias() default "";
  String expression() default "";
  String defaultValue() default "";
}
