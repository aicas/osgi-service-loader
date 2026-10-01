/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.example.consumer.v2;

import java.util.ServiceLoader;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import com.aicas.osgi.spi.example.spi.SPIProvider;

/** Plain ServiceLoader consumer for the version 2 SPI package. */
public class ServiceloaderConsumer2Activator implements BundleActivator
{
  @Override
  public void start(BundleContext context)
  {
    ServiceLoader.load(SPIProvider.class).findFirst().ifPresentOrElse(
        provider -> System.out.println(provider.getMessage()),
        () -> System.out.println("[consumerV2] No SPI 2.0 provider found."));
  }

  @Override
  public void stop(BundleContext context)
  {
    System.out.println("[consumerV2] stopped");
  }
}
