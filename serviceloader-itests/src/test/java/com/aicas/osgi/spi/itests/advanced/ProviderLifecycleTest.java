/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.advanced;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;
import org.osgi.framework.Bundle;

import com.aicas.osgi.spi.example.spi.SPIProvider;
import com.aicas.osgi.spi.itests.support.AbstractTest;
import com.aicas.osgi.spi.proxy.internal.MediatorConstants;

/** Integration tests for on-demand provider lifecycle management. */
public class ProviderLifecycleTest extends AbstractTest
{
  private static final long PROVIDER_STOP_DELAY_MILLIS = 500;

  /** Allows the delayed-stop scheduler time to run after its configured delay. */
  private static final long PROVIDER_STOP_DELAY_TOLERANCE_MILLIS = 50;

  @Override
  protected Map<String, String> frameworkProperties()
  {
    return Map.of(MediatorConstants.PROVIDER_STOP_DELAY_MILLIS_PROPERTY,
                  Long.toString(PROVIDER_STOP_DELAY_MILLIS));
  }

  /**
   * Verifies that a consumer starts resolved, stopped provider hosts on demand.
   *
   * <p>The provider hosts are resolved before the consumer starts, but remain
   * stopped until the mediator creates their provider instances. The attached
   * fragment remains resolved because fragments are never started directly.</p>
   */
  @Test
  public void startsResolvedStoppedProvidersOnDemand()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle moduleInfoProvider = install(BUNDLE_PROVIDER_MODULE_INFO);
    Bundle fragment = install(BUNDLE_PROVIDER_FRAGMENT);
    Bundle fragmentHost = install(BUNDLE_PROVIDER);
    Bundle consumer = install(BUNDLE_CONSUMER);

    resolveBundles(provider, moduleInfoProvider, fragmentHost);
    assertEquals(Bundle.RESOLVED, provider.getState());
    assertEquals(Bundle.RESOLVED, moduleInfoProvider.getState());
    assertEquals(Bundle.RESOLVED, fragmentHost.getState());
    assertEquals(Bundle.RESOLVED, fragment.getState());

    consumer.start();

    awaitOutputContains(REGULAR_PROVIDER_MESSAGE,
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

  /** Verifies that an idle provider stops after its consumer stops. */
  @Test
  public void stopsIdleProviderAfterConsumerStops()
    throws Exception
  {
    Bundle provider = install(BUNDLE_PROVIDER_METADATA);
    Bundle consumer = install(BUNDLE_CONSUMER);

    provider.start();
    consumer.start();
    awaitOutputContains(REGULAR_PROVIDER_MESSAGE);
    assertEquals(Bundle.ACTIVE, provider.getState());

    consumer.stop();
    Thread.sleep(PROVIDER_STOP_DELAY_MILLIS -
                 PROVIDER_STOP_DELAY_TOLERANCE_MILLIS);
    assertEquals("Provider stopped before the configured idle delay",
                 Bundle.ACTIVE, provider.getState());

    Thread.sleep(PROVIDER_STOP_DELAY_TOLERANCE_MILLIS);
    awaitBundleState(provider, Bundle.RESOLVED,
                     PROVIDER_STOP_DELAY_TOLERANCE_MILLIS);

    assertNull(awaitService(SPIProvider.class.getName(), false));
    assertOutputContains("[consumer] stopped");
  }
}
