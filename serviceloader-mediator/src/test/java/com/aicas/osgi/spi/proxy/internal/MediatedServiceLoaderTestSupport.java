/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.BundleProvider;
import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.RunnableProvider;
import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.TestService;
import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.callerClassFor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.log.Logger;

import com.aicas.osgi.spi.proxy.ServiceLoader;

/**
 * Test-only support for exercising the mediator-backed {@link ServiceLoader}
 * path without exposing mediator implementation details from production code.
 */
public final class MediatedServiceLoaderTestSupport
{
  private MediatedServiceLoaderTestSupport()
  {
  }

  /** Installs an isolated mediator environment for one test. */
  public static MediatorEnvironment install()
  {
    return new MediatorEnvironment();
  }

  /** Owns the temporary mediator state used by a single test. */
  public static final class MediatorEnvironment implements AutoCloseable
  {
    private final MediatorActivator previousActivator_ =
      MediatorActivator.activator_;
    private final Logger previousLogger_ = MediatorActivator.logger_;
    private final ProviderBundleLifecycleManager lifecycleManager_;

    private MediatorEnvironment()
    {
      MediatorActivator.logger_ = mock(Logger.class);
      lifecycleManager_ =
        new ProviderBundleLifecycleManager(60_000L,
                                           () -> MediatorActivator.logger_,
                                           ignored -> {
                                           });
    }

    /** Creates a consumer and provider fixture backed by the temporary mediator. */
    public MediatedFixture createFixture(long providerBundleId)
      throws ReflectiveOperationException
    {
      return new MediatedFixture(providerBundleId, lifecycleManager_);
    }

    @Override
    public void close()
    {
      lifecycleManager_.close();
      MediatorActivator.activator_ = previousActivator_;
      MediatorActivator.logger_ = previousLogger_;
    }
  }

  /** A complete mediator, consumer, and provider fixture for one test. */
  public static final class MediatedFixture
  {
    private final MediatorActivator mediator_ = new MediatorActivator();
    private final Bundle providerBundle_ = mock(Bundle.class);
    private final Bundle consumerBundle_ = mock(Bundle.class);
    private final TrackingProviderClassLoader providerClassLoader_ =
      new TrackingProviderClassLoader();
    private final AtomicInteger state_ = new AtomicInteger(Bundle.ACTIVE);

    private MediatedFixture(long providerBundleId,
                            ProviderBundleLifecycleManager lifecycleManager)
      throws ReflectiveOperationException
    {
      BundleContext context = mock(BundleContext.class);
      BundleRevision providerRevision = mock(BundleRevision.class);
      BundleWiring providerWiring = mock(BundleWiring.class);
      BundleWiring consumerWiring = mock(BundleWiring.class);
      BundleCapability servicePackageCapability = mock(BundleCapability.class);

      when(providerBundle_.getBundleId()).thenReturn(providerBundleId);
      when(providerBundle_.getState()).thenAnswer(ignored -> state_.get());
      when(providerBundle_.adapt(BundleRevision.class)).
           thenReturn(providerRevision);
      when(providerBundle_.adapt(BundleWiring.class)).
           thenReturn(providerWiring);
      when(providerRevision.getBundle()).thenReturn(providerBundle_);
      when(providerWiring.getClassLoader()).thenReturn(providerClassLoader_);
      when(providerWiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE)).
           thenReturn(List.of());
      when(providerWiring.getCapabilities(BundleRevision.PACKAGE_NAMESPACE)).
           thenReturn(List.of(servicePackageCapability));

      when(consumerBundle_.adapt(BundleWiring.class)).
           thenReturn(consumerWiring);
      when(consumerWiring.getRequiredWires(BundleRevision.PACKAGE_NAMESPACE)).
           thenReturn(List.of());
      when(consumerWiring.getCapabilities(BundleRevision.PACKAGE_NAMESPACE)).
           thenReturn(List.of(servicePackageCapability));
      when(servicePackageCapability.getAttributes()).
           thenReturn(Map.of(BundleRevision.PACKAGE_NAMESPACE,
                             TestService.class.getPackageName()));

      when(context.getBundle(providerBundleId)).thenReturn(providerBundle_);

      setField(mediator_, "bundleContext_", context);
      setField(mediator_, "providerBundleLifecycleManager_", lifecycleManager);
      mediator_.registerServiceProviderEntries(providerBundle_,
                                               new ProviderEntry(TestService.class.getName(),
                                                                 providerBundle_,
                                                                 servicePackageCapability,
                                                                 Set.of(BundleProvider.class.getName())));
      MediatorActivator.activator_ = mediator_;
    }

    /** Returns a loader for the fixture's mediated service type. */
    public ServiceLoader<TestService> loader()
    {
      return ServiceLoader.load(TestService.class,
                                callerClassFor(consumerBundle_));
    }

    /** Makes the provider Bundle unavailable without discarding prepared providers. */
    public void stopProviderBundle()
    {
      state_.set(Bundle.RESOLVED);
    }

    /** Registers a provider for another service type. */
    public void registerUnrelatedProvider()
    {
      mediator_.registerServiceProviderEntries(providerBundle_,
                                               new ProviderEntry(Runnable.class.getName(),
                                                                 providerBundle_,
                                                                 mock(BundleCapability.class),
                                                                 Set.of(RunnableProvider.class.getName())));
    }

    /** Returns the mocked provider Bundle for interaction assertions. */
    public Bundle providerBundle()
    {
      return providerBundle_;
    }

    /** Returns class names requested through the provider Bundle loader. */
    public Set<String> requestedProviderClassNames()
    {
      return providerClassLoader_.requestedClassNames();
    }
  }

  /** Class loader supplied by the mocked provider wiring. */
  private static final class TrackingProviderClassLoader extends ClassLoader
  {
    private final Set<String> requestedClassNames_ =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

    private TrackingProviderClassLoader()
    {
      super(MediatedServiceLoaderTestSupport.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name,
                                 boolean resolve)
      throws ClassNotFoundException
    {
      if (BundleProvider.class.getName().equals(name))
        {
          requestedClassNames_.add(name);
          return BundleProvider.class;
        }
      return super.loadClass(name, resolve);
    }

    private Set<String> requestedClassNames()
    {
      return Set.copyOf(requestedClassNames_);
    }
  }

  private static void setField(MediatorActivator mediator,
                               String fieldName,
                               Object value)
    throws ReflectiveOperationException
  {
    Field field = MediatorActivator.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(mediator, value);
  }
}
