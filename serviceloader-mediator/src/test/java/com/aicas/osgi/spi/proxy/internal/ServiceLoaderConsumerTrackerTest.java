/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Namespace;
import org.osgi.service.log.Logger;


/** Tests consumer metadata discovery and lifecycle refresh behavior. */
public class ServiceLoaderConsumerTrackerTest
{
  private static final String OSGI_SERVICE_TYPE =
      "example.osgi.ConsumerService";

  private Logger previousLogger;

  @Before
  public void setUpLogger()
  {
    previousLogger = MediatorActivator.logger_;
    MediatorActivator.logger_ = mock(Logger.class);
  }

  @After
  public void restoreLogger()
  {
    MediatorActivator.logger_ = previousLogger;
  }

  @Test
  public void ignoresConsumerWiredToAnotherMediator()
  {
    Bundle thisMediator = mock(Bundle.class);
    Bundle otherMediator = mock(Bundle.class);
    Bundle bundle = bundleWithRevision(consumerRevision(OSGI_SERVICE_TYPE,
                                                        otherMediator));
    MediatorActivator activator = mock(MediatorActivator.class);
    when(activator.getMediatorBundle()).thenReturn(thisMediator);

    new ServiceLoaderConsumerTracker(activator).addingBundle(bundle, null);

    verify(activator).getMediatorBundle();
    verifyNoMoreInteractions(activator);
  }

  @Test
  public void ignoresMultipleCardinalityProcessorRequirement()
  {
    Bundle mediatorBundle = mock(Bundle.class);
    BundleRevision revision = consumerRevision(OSGI_SERVICE_TYPE, mediatorBundle);
    BundleRequirement requirement =
        revision.getDeclaredRequirements(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE).get(0);
    when(requirement.getDirectives()).
         thenReturn(Map.of(MediatorConstants.FILTER_DIRECTIVE,
                           "(osgi.extender=osgi.serviceloader.processor)",
                           Namespace.REQUIREMENT_CARDINALITY_DIRECTIVE,
                           Namespace.CARDINALITY_MULTIPLE));
    MediatorActivator activator = mock(MediatorActivator.class);
    when(activator.getMediatorBundle()).thenReturn(mediatorBundle);

    new ServiceLoaderConsumerTracker(activator).
          addingBundle(bundleWithRevision(revision), null);

    verify(activator).getMediatorBundle();
    verifyNoMoreInteractions(activator);
  }

  @Test
  public void ignoresInvalidConsumerRequirementFilter()
  {
    BundleRevision revision = mock(BundleRevision.class);
    BundleRequirement extenderRequirement = mock(BundleRequirement.class);
    when(revision.getTypes()).thenReturn(0);
    when(revision.getWiring()).thenReturn(null);
    when(revision.getDeclaredRequirements(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE)).
         thenReturn(Collections.singletonList(extenderRequirement));
    when(extenderRequirement.getDirectives()).
         thenReturn(Map.of(MediatorConstants.FILTER_DIRECTIVE, "not a filter"));
    Bundle bundle = bundleWithRevision(revision);
    MediatorActivator activator = mock(MediatorActivator.class);

    new ServiceLoaderConsumerTracker(activator).addingBundle(bundle, null);

    verify(activator).getMediatorBundle();
    verifyNoMoreInteractions(activator);
  }

  @Test
  public void unregistersConsumerMetadataWhenBundleIsRemoved()
  {
    Bundle bundle = mock(Bundle.class);
    MediatorActivator activator = mock(MediatorActivator.class);

    new ServiceLoaderConsumerTracker(activator).removedBundle(bundle,
                                                              null, null);

    verify(activator).unregisterConsumerBundle(bundle);
  }

  private Bundle bundleWithRevision(BundleRevision revision)
  {
    Bundle bundle = mock(Bundle.class);
    when(bundle.adapt(BundleRevision.class)).thenReturn(revision);
    return bundle;
  }

  private BundleRevision consumerRevision(String serviceType, Bundle mediatorBundle)
  {
    BundleRevision revision = noConsumerMetadataRevision();
    BundleRequirement extenderRequirement = mock(BundleRequirement.class);
    BundleRequirement serviceLoaderRequirement = mock(BundleRequirement.class);
    BundleWiring wiring = mock(BundleWiring.class);
    BundleWire wire = mock(BundleWire.class);
    BundleWiring providerWiring = mock(BundleWiring.class);
    BundleCapability capability = mock(BundleCapability.class);
    when(revision.getDeclaredRequirements(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE)).
         thenReturn(Collections.singletonList(extenderRequirement));
    when(revision.getDeclaredRequirements(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE)).
         thenReturn(Collections.singletonList(serviceLoaderRequirement));
    when(extenderRequirement.getDirectives()).thenReturn(Map.of(
        MediatorConstants.FILTER_DIRECTIVE,
        "(" + MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE + "=" +
        MediatorConstants.PROCESSOR_EXTENDER_NAME + ")"));
    when(revision.getWiring()).thenReturn(wiring);
    when(wiring.getRequiredWires(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE)).
         thenReturn(Collections.singletonList(wire));
    when(wire.getRequirement()).thenReturn(extenderRequirement);
    when(wire.getProviderWiring()).thenReturn(providerWiring);
    when(providerWiring.getBundle()).thenReturn(mediatorBundle);
    when(wire.getCapability()).thenReturn(capability);
    when(capability.getAttributes()).thenReturn(Map.of(
        MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE,
        MediatorConstants.PROCESSOR_EXTENDER_NAME));
    when(serviceLoaderRequirement.getDirectives()).
         thenReturn(Map.of(MediatorConstants.FILTER_DIRECTIVE,
                           "(" + MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE +
                           "=" + serviceType + ")"));
    return revision;
  }

  private BundleRevision noConsumerMetadataRevision()
  {
    BundleRevision revision = mock(BundleRevision.class);
    when(revision.getTypes()).thenReturn(0);
    when(revision.getWiring()).thenReturn(null);
    when(revision.getBundle()).thenReturn(mock(Bundle.class));
    when(revision.getDeclaredRequirements(MediatorConstants.EXTENDER_CAPABILITY_NAMESPACE)).
         thenReturn(Collections.emptyList());
    return revision;
  }
}
