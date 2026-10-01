/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * The provider bundles visible to one processed consumer. A consumer with no
 * Service Loader requirements has unrestricted visibility. Otherwise, it can
 * use only provider bundles selected by resolved Service Loader requirement
 * wires for the requested Service Type.
 */
final class ConsumerVisibility
{
  private final boolean unrestricted_;

  private final Map<String, Set<Long>> providersByServiceType_;

  /**
   * Creates a new visibility record for a processed consumer.
   *
   * @param unrestricted whether the consumer has unrestricted visibility.
   * @param wiredProviderBundles provider bundle IDs selected by
   *        resolved wires, grouped by Service Type.
   */
  ConsumerVisibility(boolean unrestricted,
                     Map<String, Set<Long>> wiredProviderBundles)
  {
    unrestricted_ = unrestricted;

    Map<String, Set<Long>> copiedWires = new LinkedHashMap<>();
    for (Map.Entry<String, Set<Long>> entry : wiredProviderBundles.entrySet())
      {
        copiedWires.put(entry.getKey(), Set.copyOf(entry.getValue()));
      }
    providersByServiceType_ = Collections.unmodifiableMap(copiedWires);
  }

  static ConsumerVisibility unrestricted()
  {
    return new ConsumerVisibility(true, Collections.emptyMap());
  }

  static ConsumerVisibility restricted(Map<String,
                                       Set<Long>> providerByServiceType)
  {
    return new ConsumerVisibility(false,
                                  providerByServiceType);
  }

  /**
   * Creates restricted visibility from the consumer's resolved
   * {@code osgi.serviceloader} requirement wires.
   *
   * <p>Each valid wire contributes its provider bundle ID under the Service
   * Type published by the wire capability. Missing wiring, no matching wires,
   * and invalid wires produce empty restricted visibility.</p>
   *
   * @param consumer the Consumer bundle whose resolved wires are inspected.
   * @return visibility limited to provider bundles selected by resolved wires.
   */
  static ConsumerVisibility fromResolvedWires(Bundle consumer)
  {
    Map<String, Set<Long>> providerBundleIdsByServiceType = new HashMap<>();
    BundleWiring wiring = consumer.adapt(BundleWiring.class);
    if (wiring != null)
      {
        List<BundleWire> wires =
          wiring.getRequiredWires(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE);

        if (wires != null)
          {
            for (BundleWire wire : wires)
              {
                BundleCapability capability = wire.getCapability();
                Object serviceTypeValue =
                  capability == null ? null :
                                       capability.getAttributes().
                                       get(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE);
                String serviceType =
                  serviceTypeValue instanceof String ?
                       HeaderProcessor.normalize((String) serviceTypeValue) :
                       null;

                if (serviceType == null ||
                    wire.getProviderWiring() == null ||
                    wire.getProviderWiring().getBundle() == null)
                  {
                    continue;
                  }
                providerBundleIdsByServiceType.computeIfAbsent(serviceType,
                                                               ignored -> new HashSet<>()).
                                              add(wire.getProviderWiring().
                                              getBundle().
                                              getBundleId());
              }
          }
      }
    return restricted(providerBundleIdsByServiceType);
  }

  boolean allows(String serviceType, long providerBundleId)
  {
    return unrestricted_ ||
           providersByServiceType_.getOrDefault(serviceType,
                                                Collections.emptySet()).
                                   contains(providerBundleId);
  }
}
