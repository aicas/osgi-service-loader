/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.example.provider.v2;

import com.aicas.osgi.spi.example.spi.SPIProvider;

/** Plain ServiceLoader provider for the version 2 SPI package. */
public class SPIProviderImpl2 implements SPIProvider
{
  @Override
  public String getMessage()
  {
    return "Hello, I was provided by SPI 2.0.";
  }
}
