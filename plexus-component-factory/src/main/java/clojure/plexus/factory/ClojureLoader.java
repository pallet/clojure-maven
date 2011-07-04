package clojure.plexus.factory;

import java.io.IOException;
import clojure.lang.RT;
import clojure.lang.Var;

public class ClojureLoader
{
  static public Object instantiate(String implementation) throws Exception
  {
    Var require = RT.var("clojure.core", "require");
    Var symbol = RT.var("clojure.core", "symbol");
    require.invoke(symbol.invoke("clojure.plexus.factory.component-factory"));
    Var instantiate = RT.var(
      "clojure.plexus.factory.component-factory", "instantiate");
    return instantiate.invoke(implementation);
  }
}
