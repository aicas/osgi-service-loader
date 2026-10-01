/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.util.Map;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests Service-Type-specific Consumer visibility. */
public class ConsumerVisibilityTest
{

  /**
   * Verifies that a resolved {@code osgi.serviceloader} wire for
   * {@code com.example.Codec} from provider Bundle ID {@code 12} makes that
   * provider visible only for the published {@code Codec} Service Type.
   */
  @Test
  public void resolvedWireRestrictsProviderToPublishedServiceType()
  {
    Bundle consumer = mock(Bundle.class);
    BundleWiring consumerWiring = mock(BundleWiring.class);
    BundleWire codecWire = mock(BundleWire.class);
    BundleCapability codecCapability = mock(BundleCapability.class);
    BundleWiring providerWiring = mock(BundleWiring.class);
    Bundle provider = mock(Bundle.class);

    when(consumer.adapt(BundleWiring.class)).thenReturn(consumerWiring);
    when(consumerWiring.getRequiredWires(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE)).
         thenReturn(java.util.List.of(codecWire));
    when(codecWire.getCapability()).thenReturn(codecCapability);
    when(codecCapability.getAttributes()).
         thenReturn(Map.of(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE,
                           "com.example.Codec"));
    when(codecWire.getProviderWiring()).thenReturn(providerWiring);
    when(providerWiring.getBundle()).thenReturn(provider);
    when(provider.getBundleId()).thenReturn(12L);

    ConsumerVisibility visibility = ConsumerVisibility.fromResolvedWires(consumer);

    assertTrue(visibility.allows("com.example.Codec", 12L));
    assertFalse(visibility.allows("com.example.Parser", 12L));
  }
}
