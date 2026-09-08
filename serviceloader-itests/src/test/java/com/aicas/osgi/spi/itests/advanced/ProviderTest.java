/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.advanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;

import com.aicas.osgi.spi.itests.support.AbstractTest;
import com.aicas.osgi.spi.example.spi.SPIProvider;

public class ProviderTest extends AbstractTest
{
  /**
   * Verifies that a consumer discovers every started provider exactly once.
   *
   * <p>The metadata-free Service Loader consumer iterates over all providers.
   * The provider hosts are started before the consumer so that this test covers
   * only duplicate discovery.</p>
   */
  @Test
  public void discoversAllProvidersOnce()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle moduleInfoProvider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle fragment = install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle fragmentHost = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    moduleInfoProvider.start();
    fragmentHost.start();

    consumer.start();

    awaitOutputContains(REGULAR_PROVIDER_MESSAGE,
                        MODULE_INFO_PROVIDER_MESSAGE,
                        FRAGMENT_PROVIDER_MESSAGE);
    assertOutputContainsExactlyOnce(REGULAR_PROVIDER_MESSAGE,
                                    MODULE_INFO_PROVIDER_MESSAGE,
                                    FRAGMENT_PROVIDER_MESSAGE);
    assertEquals(Bundle.ACTIVE, provider.getState());
    assertEquals(Bundle.ACTIVE, moduleInfoProvider.getState());
    assertEquals(Bundle.ACTIVE, fragmentHost.getState());
    assertEquals(Bundle.RESOLVED, fragment.getState());
    assertNotNull(awaitService(SPIProvider.class.getName(), true));
    consumer.stop();
    fragmentHost.stop();
    moduleInfoProvider.stop();
    provider.stop();

    assertNull(awaitService(SPIProvider.class.getName(), false));
    assertOutputContains("[consumer] stopped");
  }

  /**
   * Verifies that a provider construction failure is isolated by the
   * mediator and does not prevent valid providers from being consumed.
   */
  @Test
  public void ignoresFailingProvider()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle failingProvider = installTestBundle(BUNDLE_PROVIDER_FAILURE);
    Bundle consumer = install(BUNDLE_CONSUMER_METADATA);

    provider.start();
    failingProvider.start();
    consumer.start();

    awaitOutputContains("[failing provider] construction attempted",
                        REGULAR_PROVIDER_MESSAGE);
    assertOutputContainsExactlyOnce(REGULAR_PROVIDER_MESSAGE);
    assertEquals(Bundle.ACTIVE, consumer.getState());
    assertOutputDoesNotContain("[consumer] Failed");

    consumer.stop();
    failingProvider.stop();
    provider.stop();

    assertEquals(0, awaitServiceCount(SPIProvider.class.getName(), 0));
    assertOutputContains("[consumer] stopped");
  }

  /**
   * Verifies that host and fragment provider capabilities for different
   * service types are both retained.
   *
   * <p>The host declares {@link SPIProvider}, while its fragment declares
   * {@link Runnable}. Both service configuration files and the shared provider
   * implementation are in the host. With the registrar enabled on the host,
   * mediator-created OSGi registrations prove that neither capability was lost
   * while reading the combined host-and-fragment metadata.</p>
   */
  @Test
  public void retainsHostAndFragmentProviderCapabilities()
    throws Exception
  {
    Bundle fragment =
      installTestBundle(BUNDLE_TWO_SERVICE_PROVIDER_FRAGMENT);
    Bundle host = installTestBundle(BUNDLE_TWO_SERVICE_PROVIDER_HOST);

    host.start();

    assertNotNull(awaitService(SPIProvider.class.getName(), true));
    assertNotNull(awaitService(Runnable.class.getName(), true));
    assertEquals(Bundle.RESOLVED, fragment.getState());

    host.stop();

    assertNull(awaitService(SPIProvider.class.getName(), false));
    assertNull(awaitService(Runnable.class.getName(), false));
  }

  /**
   * Verifies that restarting a metadata provider removes its old OSGi service
   * registration and creates a new one while the OSGi client is running.
   */
  @Test
  public void recreatesTheOsgiServiceAfterProviderRestart()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle osgiConsumer = install(BUNDLE_OSGI_CLIENT);

    provider.start();
    osgiConsumer.start();

    ServiceReference<?> firstReference =
      awaitService(SPIProvider.class.getName(), true);
    assertNotNull(firstReference);
    awaitOutputContains("[osgi client] - " + REGULAR_PROVIDER_MESSAGE);

    // restart the provider
    provider.stop();
    assertNull(awaitService(SPIProvider.class.getName(), false));
    provider.start();

    ServiceReference<?> restartedReference =
      awaitService(SPIProvider.class.getName(), true);
    assertNotNull(restartedReference);
    // a new service should be created after restarting.
    assertNotSame(firstReference, restartedReference);
    assertEquals(SPIProvider.class.getName(),
                 ((String[]) restartedReference.getProperty(Constants.OBJECTCLASS))[0]);

    int restartOutputOffset = outputLength();
    osgiConsumer.stop();
    osgiConsumer.start();
    awaitOutputContainsAfter(restartOutputOffset,
                             "[osgi client] - " + REGULAR_PROVIDER_MESSAGE);

    osgiConsumer.stop();
    provider.stop();

    assertNull(awaitService(SPIProvider.class.getName(), false));
    assertOutputContains("[osgi client] Stopped");
  }
}
