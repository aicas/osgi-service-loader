/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/
package com.aicas.osgi.spi.example.consumer;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.aicas.osgi.spi.example.spi.SPIProvider;

/**
 * A bundle that consumes services through the Java ServiceLoader API.
 */
public class ServiceloaderConsumerActivator implements BundleActivator
{
  @Override
  public void start(BundleContext context)
  {
    System.out.println("\n[consumer] Results from all SPIs:\n");
    try
      {
        boolean providerFound = false;
        for (SPIProvider provider : ServiceLoader.load(SPIProvider.class))
          {
            providerFound = true;
            System.out.println(provider.getMessage());
          }
        if (!providerFound)
          {
            System.out.println("[consumer] No SPI provider found.");
          }
      }
    catch (ServiceConfigurationError e)
      {
        System.err.println("[consumer] Failed to load SPI providers");
        e.printStackTrace();
      }
  }

  @Override
  public void stop(BundleContext context)
  {
    System.out.println("[consumer] stopped");
  }
}
