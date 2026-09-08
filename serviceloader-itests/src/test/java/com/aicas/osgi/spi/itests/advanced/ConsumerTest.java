/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.advanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.osgi.framework.Bundle;

import com.aicas.osgi.spi.itests.support.AbstractTest;
import com.aicas.osgi.spi.example.spi.SPIProvider;

/** Verifies consumer behavior independent of a particular metadata form. */
public class ConsumerTest extends AbstractTest
{
  /** Verifies that a consumer can start when no provider bundle is available. */
  @Test
  public void startsConsumerWithoutProviders()
    throws Exception
  {
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);
    consumer.start();

    assertEquals(Bundle.ACTIVE, consumer.getState());
    assertNull(awaitService(SPIProvider.class.getName(), false));
    awaitOutputContains("[consumer] No provider found.");

    consumer.stop();
    assertOutputContains("[consumer] stopped");
  }

  /** Verifies that ServiceLoader calls in an embedded JAR are woven. */
  @Test
  public void processesConsumerCodeInEmbeddedJar()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer =
      installTestBundle(BUNDLE_EMBEDDED_CONSUMER_BUNDLE);

    provider.start();
    consumer.start();

    assertEquals(Bundle.ACTIVE, consumer.getState());
    awaitOutputContains("[embedded consumer] " + REGULAR_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
    assertOutputContains("[embedded consumer] stopped");
  }

}
