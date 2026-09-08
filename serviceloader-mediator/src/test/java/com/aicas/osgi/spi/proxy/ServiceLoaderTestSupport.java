/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy;

import java.lang.reflect.Proxy;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

/** Shared service types, providers, and OSGi caller fixtures for loader tests. */
public final class ServiceLoaderTestSupport
{
  private ServiceLoaderTestSupport()
  {
  }

  public interface TestService
  {
    String value();
  }

  public static final class JavaProvider implements TestService
  {
    public JavaProvider()
    {
    }

    @Override
    public String value()
    {
      return "JavaProvider";
    }
  }

  /** Provider implementation deliberately exposed only through bundle wiring. */
  public static final class BundleProvider implements TestService
  {
    public BundleProvider()
    {
    }

    @Override
    public String value()
    {
      return "BundleProvider";
    }
  }

  public static final class RunnableProvider implements Runnable
  {
    @Override
    public void run()
    {
    }
  }

  /** Creates a class whose defining loader identifies the supplied OSGi consumer bundle. */
  public static Class<?> callerClassFor(Bundle consumerBundle)
  {
    return Proxy.newProxyInstance(new BundleReferenceClassLoader(consumerBundle),
                          new Class<?>[]{ Runnable.class },
                          (proxy, method, arguments) -> null).getClass();
  }

  /** Defines generated proxy classes while exposing an OSGi consumer bundle. */
  private static final class BundleReferenceClassLoader extends ClassLoader
    implements BundleReference
  {
    private final Bundle bundle;

    private BundleReferenceClassLoader(Bundle bundle)
    {
      super(ServiceLoaderTestSupport.class.getClassLoader());
      this.bundle = bundle;
    }

    @Override
    public Bundle getBundle()
    {
      return bundle;
    }
  }
}
