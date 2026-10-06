/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy;

/** Shared service types and providers for loader tests. */
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

}
