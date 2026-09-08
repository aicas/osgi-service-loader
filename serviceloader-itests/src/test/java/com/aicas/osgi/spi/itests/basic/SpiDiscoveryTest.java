/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.basic;

import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.osgi.framework.Bundle;

import com.aicas.osgi.spi.example.spi.SPIProvider;
import com.aicas.osgi.spi.itests.support.AbstractTest;

/**
 * Verifies ServiceLoader provider discovery without OSGi ServiceLoader
 * metadata, including module-descriptor declarations and SPI package-version
 * isolation.
 */
public class SpiDiscoveryTest extends AbstractTest
{
  /**
   * Verifies that a consumer discovers a provider declared through
   * {@code META-INF/services}, even though neither Bundle declares OSGi
   * ServiceLoader metadata.
   */
  @Test
  public void consumerDiscoversHostProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Results from all SPIs:",
                             FRAGMENT_PROVIDER_MESSAGE);
    consumer.stop();
    provider.stop();
  }

  /**
   * Verifies that a consumer discovers a provider declared in the provider
   * Bundle's {@code module-info.class}.
   */
  @Test
  public void consumerDiscoversModuleInfoProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Results from all SPIs:",
                             MODULE_INFO_PROVIDER_MESSAGE);
    consumer.stop();
    provider.stop();
  }

  /**
   * Verifies that a consumer importing the version 2 SPI discovers a provider
   * that implements the same version of that SPI.
   */
  @Test
  public void consumerV2DiscoversProviderV2()
    throws Exception
  {
    Bundle provider = installTestBundle(BUNDLE_PROVIDER_V2);
    Bundle consumer = installTestBundle(BUNDLE_CONSUMER_V2);

    provider.start();
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset, V2_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
  }

  /**
   * Verifies that a consumer importing the version 2 SPI does not accept a
   * provider wired to the incompatible version 1 SPI package.
   */
  @Test
  public void consumerV2DoesNotDiscoverProviderV1()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER);
    Bundle consumer = installTestBundle(BUNDLE_CONSUMER_V2);

    provider.start();
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumerV2] No SPI 2.0 provider found.");
    assertOutputDoesNotContain(FRAGMENT_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
  }

}
