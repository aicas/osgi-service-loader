/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.example.consumer.v2;

import java.util.ServiceLoader;

import com.aicas.osgi.spi.example.spi.SPIProvider;

/**
 * Exercises the explicit-class-loader {@link ServiceLoader} API supported by
 * the OSGi mediator.
 *
 * <p>Passing this consumer Bundle's class loader enables mediated provider
 * discovery. A different class loader retains normal JDK ServiceLoader
 * lookup.</p>
 */
public final class SupportedApiConsumer
{
  private SupportedApiConsumer()
  {
  }

  /**
   * Performs {@link ServiceLoader#load(Class, ClassLoader)} for the SPI using
   * the supplied class loader and prints the lookup result.
   *
   * <p>If a provider is found, its message is printed. Otherwise, a
   * no-provider message is printed.</p>
   *
   * @param classLoader class loader supplied to {@link ServiceLoader#load}
   */
  public static void loadServiceWithGivenClassLoader(ClassLoader classLoader)
  {
    ServiceLoader.load(SPIProvider.class, classLoader)
        .findFirst()
        .ifPresentOrElse(provider -> System.out.println(provider.getMessage()),
                         () -> System.out.println(
                             "[supportedApiConsumer] No provider found."));
  }

  /**
   * Returns the diagnostic string for an SPI ServiceLoader using the supplied
   * class loader.
   *
   * @param classLoader class loader supplied to {@link ServiceLoader#load}
   * @return the ServiceLoader diagnostic string
   */
  public static String serviceLoaderToString(ClassLoader classLoader)
  {
    return ServiceLoader.load(SPIProvider.class, classLoader).toString();
  }
}
