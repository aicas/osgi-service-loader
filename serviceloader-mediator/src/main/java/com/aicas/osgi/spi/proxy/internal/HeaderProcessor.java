/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.util.*;
import java.util.regex.Pattern;

import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.resource.Namespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

public class HeaderProcessor
{
  /** The kind of Service Loader metadata declared by a bundle revision. */
  enum MetadataKind
  {
    /** A declared {@code osgi.serviceloader} requirement. */
    REQUIREMENT,

    /** A declared {@code osgi.serviceloader} capability. */
    CAPABILITY
  }

  /**
  * Removes leading and trailing whitespace from the specified string.
  *
  * <p>If the input is {@code null}, empty, or contains only whitespace,
  * {@code null} is returned. Otherwise, the trimmed string is returned.</p>
  *
  * @param str the string to trim, or {@code null}.
  *
  * @return the trimmed string, or {@code null} if the input is
  *         {@code null}, empty, or contains only whitespace.
  */
  static String normalize(String str)
  {
    if (str == null)
      {
        return null;
      }
    str = str.trim();
    if ( str.length() == 0)
      {
        return null;
      }
    return str;
  }

  /**
   * Returns the current revision of the given bundle and the revisions of all
   * fragments currently attached to it.
   *
   * @param bundle the host bundle.
   *
   * @return the host revision followed by attached fragment revisions; the list
   *         is empty if the bundle is {@code null} or has no current revision.
   */
  static List<BundleRevision> getHostAndFragmentRevisions(Bundle bundle)
  {
    List<BundleRevision> revisions = new ArrayList<>();
    if (bundle != null)
      {
        BundleRevision hostRevision = bundle.adapt(BundleRevision.class);
        if (hostRevision != null)
          {
            revisions.add(hostRevision);

            BundleWiring wiring = hostRevision.getWiring();
            if (wiring != null)
              {
                for (BundleWire wire : wiring.getProvidedWires(HostNamespace.HOST_NAMESPACE))
                  {
                    BundleRevision fragmentRevision = wire.getRequirement().getRevision();

                    if (fragmentRevision != null)
                      {
                        revisions.add(fragmentRevision);
                      }
                  }
              }
            return revisions;
          }
      }
    return revisions;
  }

  /**
   * Returns whether a bundle or one of its attached fragments declares Service
   * Loader metadata of the requested kind.
   *
   * <p>This method examines declarations only. It does not inspect resolved
   * wires, so an optional or unresolved requirement is still reported.</p>
   *
   * @param bundle the host bundle to inspect.
   * @param kind whether to inspect requirements or capabilities.
   * @return {@code true} if the requested metadata is declared; {@code false}
   *         otherwise.
   */
  static boolean hasServiceLoaderMetadata(Bundle bundle,
                                          MetadataKind kind)
  {
    Objects.requireNonNull(kind, "kind");
    for (BundleRevision revision : getHostAndFragmentRevisions(bundle))
      {
        if (kind == MetadataKind.REQUIREMENT)
          {
            if (!revision.getDeclaredRequirements(
                MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE).isEmpty())
              {
                return true;
              }
          }
        else if (!revision.getDeclaredCapabilities(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE).
                           isEmpty())
          {
            return true;
          }
      }
    return false;
  }

  /**
   * Returns whether a bundle or one of its attached fragments declares an
   * {@code osgi.serviceloader.processor} extender requirement.
   *
   * <p>This method examines declarations only. It does not inspect resolved
   * wires and therefore does not decide which mediator, if any, the Consumer
   * selected.</p>
   *
   * @param bundle the host bundle to inspect.
   * @return {@code true} if a processor extender requirement is declared;
   *         {@code false} otherwise.
   */
  static boolean hasProcessorExtenderRequirement(Bundle bundle)
  {
    return hasExtenderRequirement(bundle,
                                  MediatorConstants.PROCESSOR_EXTENDER_NAME);
  }

  /**
   * Returns whether a bundle or one of its attached fragments declares an
   * extender requirement for the supplied extender name.
   */
  private static boolean hasExtenderRequirement(Bundle bundle,
                                                String extenderName)
  {
    Objects.requireNonNull(extenderName, "extenderName");
    Pattern pattern = Pattern.compile("\\(" +
        Pattern.quote(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE) +
        "\\s*=\\s*" + Pattern.quote(extenderName) + "\\s*\\)");

    for (BundleRevision revision : getHostAndFragmentRevisions(bundle))
      {
        for (BundleRequirement requirement :
             revision.getDeclaredRequirements(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE))
          {
            String filter = requirement.getDirectives().
                                        get(MediatorConstants.FILTER_DIRECTIVE);
            if (filter != null &&
                pattern.matcher(filter).find())
              {
                return true;
              }
          }
      }
    return false;
  }

  /**
   * Returns whether an extender requirement of one of the supplied revisions
   * is actually wired to the supplied mediator bundle.
   *
   * @param revisions the host and attached fragment revisions to inspect.
   *
   * @param extenderName the expected {@code osgi.extender} capability value.
   * @param mediatorBundle the bundle that supplies this mediator's extender
   *        capability.
   * @param requireSingleCardinality whether a matching requirement declared
   *        with {@code cardinality:=multiple} must be rejected. This is
   *        {@code true} for the Consumer processor extender, which must have
   *        single cardinality, and {@code false} for the provider registrar
   *        call, where this helper does not impose that restriction.
   *
   * @return {@code true} if a matching extender requirement is found;
   *         {@code false} otherwise.
   *
   */
  static boolean hasMediatorExtenderWire(List<BundleRevision> revisions,
                                         String extenderName,
                                         Bundle mediatorBundle,
                                         boolean requireSingleCardinality)
  {
    if (mediatorBundle == null || extenderName == null)
      {
        return false;
      }
    for (BundleRevision revision : revisions)
      {
        BundleWiring wiring = revision.getWiring();
        if (wiring == null)
          {
            continue;
          }
        for (BundleWire wire :
             wiring.getRequiredWires(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE))
          {
            BundleRequirement requirement = wire.getRequirement();
            if (requireSingleCardinality &&
                Namespace.CARDINALITY_MULTIPLE.equals(requirement.getDirectives().
                                                      get(Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE)))
              {
                continue;
              }
            BundleCapability capability = wire.getCapability();
            if (mediatorBundle.equals(wire.getProviderWiring().getBundle()) &&
                hasExtenderName(capability, extenderName))
              {
                MediatorActivator.printDebug("[HEADER_PROCESSOR]Found mediator extender wire - " +
                                            revision.getBundle().getSymbolicName());
                return true;
              }
          }
      }
    return false;
  }

  private static boolean hasExtenderName(BundleCapability capability,
                                         String extenderName)
  {
    return capability != null &&
           extenderName.equals(capability.getAttributes().
                               get(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE));
  }
}
