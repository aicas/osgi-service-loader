/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.advanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;

import com.aicas.osgi.spi.example.spi.SPIProvider;
import com.aicas.osgi.spi.itests.support.AbstractTest;

/** Verifies consumer and provider behavior after changes. */
public class BundleUpdateTest extends AbstractTest
{
  /**
   * Verifies that an updated consumer is woven using its updated metadata.
   *
   * <p>The original consumer requires {@link SPIProvider}. Its updated
   * revision instead requires {@link Runnable}, and invokes ServiceLoader for
   * that type.</p>
   */
  @Test
  public void processesUpdatedConsumerMetadata()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    provider.start();
    consumer.start();
    awaitOutputContains(REGULAR_PROVIDER_MESSAGE);

    consumer.stop();
    Bundle fragment = installTestBundle(BUNDLE_TWO_SERVICE_PROVIDER_FRAGMENT);
    Bundle host = installTestBundle(BUNDLE_TWO_SERVICE_PROVIDER_HOST);
    host.start();

    int updateOutputOffset = outputLength();
    update(consumer, BUNDLE_CONSUMER_UPDATE);
    consumer.start();

    assertEquals(Bundle.ACTIVE, consumer.getState());
    awaitOutputContainsAfter(updateOutputOffset,
                             "[updated consumer] Runnable provider found.");
    assertOutputContainsExactlyOnce(REGULAR_PROVIDER_MESSAGE,
                                    "[updated consumer] Runnable provider found.");

    consumer.stop();
    host.stop();
    provider.stop();
    assertEquals(Bundle.RESOLVED, fragment.getState());
    assertOutputContains("[updated consumer] stopped");
  }

  /**
   * Verifies that removing an attached provider fragment removes its provider
   * metadata from the host bundle after the host wiring is refreshed.
   */
  @Test
  public void removesProviderWhenFragmentIsUninstalled()
    throws Exception
  {
    Bundle fragment = install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle host = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    host.start();
    consumer.start();
    awaitOutputContains(FRAGMENT_PROVIDER_MESSAGE);
    assertEquals(Bundle.ACTIVE, host.getState());

    consumer.stop();
    int removalOutputOffset = outputLength();
    fragment.uninstall();
    refreshBundles(host);

    consumer.start();
    awaitOutputContainsAfter(removalOutputOffset,
                             "[consumer] No provider found.");

    consumer.stop();
    host.stop();

    assertOutputContains("[consumer] stopped");
  }

  /**
   * Verifies that updating a provider bundle replaces its mediator definition
   * and OSGi service registration with the definition from the new revision.
   */
  @Test
  public void discoversProviderUpdate()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);
    Bundle osgiConsumer = install(BUNDLE_OSGI_CLIENT);

    provider.start();
    consumer.start();
    osgiConsumer.start();
    awaitOutputContains(REGULAR_PROVIDER_MESSAGE);
    awaitOutputContains("[osgi client] - " + REGULAR_PROVIDER_MESSAGE);

    ServiceReference<?> oldReference =
      awaitService(SPIProvider.class.getName(), true);
    assertNotNull(oldReference);

    int updateOutputOffset = outputLength();
    update(provider, BUNDLE_PROVIDER_UPDATE);

    ServiceReference<?> newReference =
      awaitService(SPIProvider.class.getName(), true);
    assertNotNull(newReference);
    assertNotSame(oldReference, newReference);
    assertEquals(provider, newReference.getBundle());
    assertEquals(1, awaitServiceCount(SPIProvider.class.getName(), 1));

    consumer.stop();
    osgiConsumer.stop();
    consumer.start();
    osgiConsumer.start();
    awaitOutputContainsAfter(updateOutputOffset,
                             UPDATED_PROVIDER_MESSAGE,
                             "[osgi client] - " + UPDATED_PROVIDER_MESSAGE);
    consumer.stop();
    osgiConsumer.stop();
    provider.stop();
    assertEquals(0, awaitServiceCount(SPIProvider.class.getName(), 0));
    assertOutputContains("[consumer] stopped", "[osgi client] Stopped");
  }

  /**
   * Verifies that updating a module-info provider replaces its old provider
   * definition with the definition from the updated module descriptor.
   */
  @Test
  public void discoversUpdatedModuleInfoProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    consumer.start();
    awaitOutputContains(MODULE_INFO_PROVIDER_MESSAGE);

    consumer.stop();
    update(provider, BUNDLE_PROVIDER_MODULE_INFO_UPDATE);

    consumer.start();
    awaitOutputContains(UPDATED_MODULE_INFO_PROVIDER_MESSAGE);
    assertOutputContainsExactlyOnce(MODULE_INFO_PROVIDER_MESSAGE,
                                    UPDATED_MODULE_INFO_PROVIDER_MESSAGE);

    consumer.stop();
    provider.stop();
    assertOutputContains("[consumer] stopped");
  }
}
