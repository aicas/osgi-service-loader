/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

import com.aicas.osgi.spi.example.spi.SPIProvider;
import com.aicas.osgi.spi.itests.support.AbstractTest;

/** Verifies discovery when both provider and consumer use OSGi ServiceLoader metadata. */
public class OsgiMetadataTest extends AbstractTest
{
  @Test
  public void metadataConsumerDiscoversMetadataProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    provider.start();
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Get all the registered services:/",
                             REGULAR_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
  }

  @Test
  public void metadataConsumerDiscoversFragmentProvider()
    throws Exception
  {
    Bundle fragment = install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle provider = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    provider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    consumer.start();

    awaitOutputContainsAfter(outputOffset,
                             "[consumer] Get all the registered services:/",
                             FRAGMENT_PROVIDER_MESSAGE);
    assertEquals(Bundle.RESOLVED, fragment.getState());

    consumer.stop();
    provider.stop();
  }

  @Test
  public void osgiClientDiscoversMetadataProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle client = install(BUNDLE_OSGI_CLIENT);

    provider.start();
    ServiceReference<?> reference = awaitService(SPIProvider.class.getName(),
                                                 true);
    assertNotNull(reference);
    int outputOffset = outputLength();
    client.start();

    awaitOutputContainsAfter(outputOffset,
                             "[osgi client] - " + REGULAR_PROVIDER_MESSAGE);

    client.stop();
    provider.stop();
  }

  @Test
  public void osgiClientDoesNotDiscoverFragmentProvider()
    throws Exception
  {
    Bundle fragment = install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle provider = install(BUNDLE_PROVIDER);
    Bundle client = install(BUNDLE_OSGI_CLIENT);

    provider.start();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    int outputOffset = outputLength();
    client.start();

    awaitOutputContainsAfter(outputOffset,
                             "[osgi client] SPIProvider service not found");
    assertEquals(Bundle.RESOLVED, fragment.getState());

    client.stop();
    provider.stop();
  }
}
