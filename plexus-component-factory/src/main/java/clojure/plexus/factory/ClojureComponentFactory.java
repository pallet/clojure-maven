package clojure.plexus.factory;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.classworlds.ClassRealm;
import org.codehaus.plexus.component.factory.AbstractComponentFactory;
import org.codehaus.plexus.component.factory.ComponentInstantiationException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;

import java.io.IOException;
import java.lang.reflect.Method;

public class ClojureComponentFactory
    extends AbstractComponentFactory
{
  public Object newInstance( ComponentDescriptor componentDescriptor,
                             ClassRealm classRealm,
                             PlexusContainer container )
    throws ComponentInstantiationException
  {
    ClassLoader currentContextLoader =
      Thread.currentThread().getContextClassLoader();

    try
    {
      ClassLoader cl = classRealm.getClassLoader();
      Class loader = classRealm.loadClass("clojure.plexus.factory.ClojureLoader");
      Method m = loader.getDeclaredMethod("instantiate",String.class);

      Thread.currentThread().setContextClassLoader(cl);
      Object obj=m.invoke(loader,componentDescriptor.getImplementation());
      if ((obj.getClass().getClassLoader() != cl) &&
          (obj.getClass().getClassLoader().getParent() != cl) &&
          (obj.getClass().getClassLoader().getParent().getParent() != cl))
      {
        throw new ComponentInstantiationException(
          "Wrong classloader for clojure component: "
          + componentDescriptor.getHumanReadableKey());
      }
      return obj;
    }
    catch ( Exception e )
    {
      classRealm.display();
      throw new ComponentInstantiationException(
        "Failed to extract Clojure component for: "
        + componentDescriptor.getHumanReadableKey(), e );
    }
    finally
    {
      Thread.currentThread() .setContextClassLoader(currentContextLoader);
    }
  }

  public String getId()
  {
    return "clojure";
  }
}
