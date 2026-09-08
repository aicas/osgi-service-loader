/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;

/** Tests provider lookup after consumer visibility and package filtering. */
public class MediatorProviderLookupTest
{
  private static final String SERVICE_TYPE = "com.example.Codec";

  @Test
  public void restrictedConsumerSeesOnlyItsWiredProvider()
  {
    MediatorActivator mediator = new MediatorActivator();
    Bundle consumer = mock(Bundle.class);
    Bundle allowedProvider = provider(1L);
    Bundle excludedProvider = provider(2L);

    mediator.registerConsumerBundle(consumer, ConsumerVisibility.restricted(
        Map.of(SERVICE_TYPE, Set.of(1L))));
    registerProvider(mediator, allowedProvider, "example.AllowedCodec");
    registerProvider(mediator, excludedProvider, "example.ExcludedCodec");

    Map<Long, Set<String>> providers = new HashMap<>();
    mediator.getProviders(SERVICE_TYPE, consumer, providers);

    assertEquals(Map.of(1L, Set.of("example.AllowedCodec")), providers);
  }

  @Test
  public void metadataFreeConsumerSeesEveryCompatibleProvider()
  {
    MediatorActivator mediator = new MediatorActivator();
    Bundle consumer = mock(Bundle.class);
    Bundle firstProvider = provider(1L);
    Bundle secondProvider = provider(2L);

    registerProvider(mediator, firstProvider, "example.FirstCodec");
    registerProvider(mediator, secondProvider, "example.SecondCodec");

    Map<Long, Set<String>> providers = new HashMap<>();
    mediator.getProviders(SERVICE_TYPE, consumer, providers);

    assertEquals(Map.of(1L, Set.of("example.FirstCodec"),
                        2L, Set.of("example.SecondCodec")), providers);
  }

  private static Bundle provider(long bundleId)
  {
    Bundle bundle = mock(Bundle.class);
    when(bundle.getBundleId()).thenReturn(bundleId);
    return bundle;
  }

  private static void registerProvider(MediatorActivator mediator,
                                       Bundle bundle,
                                       String implementationClass)
  {
    mediator.registerServiceProviderEntries(bundle,
        new ProviderEntry(SERVICE_TYPE, bundle, null,
                          Set.of(implementationClass)));
    mediator.registerServiceproviderCapabilities(bundle,
        new ProviderCapability(SERVICE_TYPE, mock(BundleCapability.class), bundle));
  }
}
