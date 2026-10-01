/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.example.consumer.v2;

import java.util.ServiceLoader;

import com.aicas.osgi.spi.example.spi.SPIProvider;

/** Exercises the unsupported JDK {@link ServiceLoader#stream()} API. */
public final class StreamConsumer
{
  private StreamConsumer()
  {
  }

  /** Executes a normal JDK ServiceLoader stream lookup. */
  public static void loadFirstProvider()
  {
    ServiceLoader.load(SPIProvider.class).stream().findFirst();
  }
  
}
