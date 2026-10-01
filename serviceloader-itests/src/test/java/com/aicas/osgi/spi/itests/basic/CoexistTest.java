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
 *  Verifies discovery when only one participant declares OSGi metadata.
 *  R5: Compatibility with Service Loader Mediator
 * */
public class CoexistTest extends AbstractTest
{
  @Test
  public void metadataConsumerDoesNotDiscoverMetadataFreeProviders()
    throws Exception
  {
    Bundle host = install(BUNDLE_PROVIDER);
    Bundle moduleInfoProvider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    host.start();
    moduleInfoProvider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset, "[consumer] No provider found.");

    consumer.stop();
    moduleInfoProvider.stop();
    host.stop();
  }

  @Test
  public void osgiClientDoesNotDiscoverMetadataFreeProviders()
    throws Exception
  {
    Bundle host = install(BUNDLE_PROVIDER);
    Bundle moduleInfoProvider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle client = install(BUNDLE_OSGI_CLIENT);

    host.start();
    moduleInfoProvider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    client.start();

    awaitOutputContainsAfter(outputOffset,
                             "[osgi client] SPIProvider service not found");

    client.stop();
    moduleInfoProvider.stop();
    host.stop();
  }

  @Test
  public void metadataFreeConsumerDiscoversMetadataProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Results from all SPIs:",
                             REGULAR_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
  }

  @Test
  public void metadataFreeConsumerDiscoversFragmentProvider()
    throws Exception
  {
    install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle provider = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Results from all SPIs:",
                             FRAGMENT_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
  }
}
