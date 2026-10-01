/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * All Service Provider implementations of one Service Type discovered in one
 * Provider bundle.
 *
 * <p>Instances describe the discovered implementations independently of
 * optional {@code osgi.serviceloader} capability metadata. That metadata is
 * represented separately by {@link ProviderCapability},
 * because it controls exposure and OSGi service registration rather than
 * identifying Provider implementations.</p>
 *
 * @param serviceType the fully qualified name of the Service Type implemented
 *        by every class in {@code implementationClasses}.
 * @param providerBundle the bundle that contains the Provider implementation
 *        classes.
 * @param packageCapability the resolved {@code osgi.wiring.package}
 *        capability for the Service Type's package. It is {@code null} when no
 *        package capability applies, for example for a private copy or boot
 *        delegation.
 * @param implementationClasses the fully qualified names of the Provider
 *        implementation classes. The list is copied when this entry is
 *        created.
 */
record ProviderEntry(String serviceType,
                     Bundle providerBundle,
                     BundleCapability packageCapability,
                     Set<String> implementationClasses)
{
  ProviderEntry
  {
    serviceType = Objects.requireNonNull(serviceType, "serviceType");
    providerBundle = Objects.requireNonNull(providerBundle, "providerBundle");
    implementationClasses = Set.copyOf(
        Objects.requireNonNull(implementationClasses, "implementationClasses"));
  }

  /**
   * Gets the resolved package capability supplying the Service Type to a
   * Provider bundle.
   *
   * <p>A Provider normally imports the Service Type package, so the method
   * first returns the exporter capability selected by its matching required
   * package wire. If no matching import wire exists, the Provider may instead
   * export the Service Type package itself; the method then returns that
   * provided package capability.</p>
   *
   * @param providerBundle the Provider bundle whose wiring is inspected.
   * @param serviceType the fully qualified name of the Service Type.
   * @return the Service Type package capability, or {@code null} when the
   *         bundle is unresolved or no package capability supplies the type.
   */
  static BundleCapability getPackageCapability(Bundle providerBundle,
                                               String serviceType)
  {
    Objects.requireNonNull(providerBundle, "providerBundle");
    Objects.requireNonNull(serviceType, "serviceType");

    String packageName = packageOf(serviceType);
    BundleWiring wiring = providerBundle.adapt(BundleWiring.class);
    if (wiring == null)
      {
        return null;
      }

    List<BundleWire> requiredWires =
        wiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE);
    if (requiredWires != null)
      {
        for (BundleWire wire : requiredWires)
          {
            BundleCapability capability = wire.getCapability();
            if (capability != null &&
                    packageName.equals(capability.getAttributes().
                            get(BundleRevision.PACKAGE_NAMESPACE)))
              {
                return capability;
              }
          }
      }

    List<BundleCapability> capabilities =
        wiring.getCapabilities(BundleRevision.PACKAGE_NAMESPACE);
    if (capabilities != null)
      {
        for (BundleCapability capability : capabilities)
          {
            if (packageName.equals(
                capability.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE)))
              {
                return capability;
              }
          }
      }
    return null;
  }

  static String packageOf(String className) {
    int dollar = className.indexOf('$');
    String outer = dollar > 0 ? className.substring(0, dollar) : className;
    int dot = outer.lastIndexOf('.');
    return dot > 0 ? outer.substring(0, dot) : "";
  }

  /**
   * @return the package capability the bundle is wired to for {@code pkg}
   *         (import), or its own export/contained capability, or {@code null}
   */
  static BundleCapability packageCapability(Bundle bundle, String pkg) {
    BundleWiring wiring = bundle.adapt(BundleWiring.class);
    if (wiring == null || pkg.isEmpty()) {
      return null;
    }
    List<BundleWire> wires = wiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE);
    if (wires != null) {
      for (BundleWire wire : wires) {
        if (pkg.equals(wire.getCapability().getAttributes().get(BundleRevision.PACKAGE_NAMESPACE))) {
          return wire.getCapability();
        }
      }
    }
    List<BundleCapability> own = wiring.getCapabilities(BundleRevision.PACKAGE_NAMESPACE);
    if (own != null) {
      for (BundleCapability capability : own) {
        if (pkg.equals(capability.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE))) {
          return capability;
        }
      }
    }
    return null;
  }

  static boolean sameCapability(BundleCapability a, BundleCapability b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.equals(b) || (a.getRevision().equals(b.getRevision()) && a.getAttributes().equals(b.getAttributes()));
  }
}
