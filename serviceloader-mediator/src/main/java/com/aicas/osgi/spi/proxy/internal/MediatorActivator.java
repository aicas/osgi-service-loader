/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.util.tracker.BundleTracker;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

public class MediatorActivator implements BundleActivator
{
  /*--------------------------- constants -----------------------------*/
  private static final int CONSUMER_TRACKED_STATES = Bundle.RESOLVED |
                                                     Bundle.STARTING |
                                                     Bundle.ACTIVE;

  private static final int PROVIDER_TRACKED_STATES = Bundle.RESOLVED |
                                                     Bundle.STARTING |
                                                     Bundle.ACTIVE |
                                                     Bundle.STOPPING;

  // for internal debug.
  private static final boolean DEBUG = false;
  /*--------------------------- variables -----------------------------*/

  public static volatile MediatorActivator activator_;

  /** Logger used when the optional OSGi Log Service is not available. */
  private static final Logger NO_OP_LOGGER = createNoOpLogger();

  /** The current logger; defaults to a no-op logger until Log Service appears. */
  public static volatile Logger logger_ = NO_OP_LOGGER;

  private ServiceTracker<LoggerFactory, LoggerFactory> loggerFactoryTracker_;

  private BundleContext bundleContext_;

  @SuppressWarnings("rawtypes")
  private BundleTracker consumerBundleTracker_;
  @SuppressWarnings("rawtypes")
  private BundleTracker providerBundleTracker_;
  @SuppressWarnings("rawtypes")
  private ServiceRegistration weavingHookService_;
  private final ConcurrentMap<String, Map<Long, List<ProviderCapability>>>
    serviceLoaderCapabilities_ = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, Map<Long, ProviderEntry>>
    serviceLoaderEntries_ = new ConcurrentHashMap<>();

  /**
   * Visibility records for processed consumer bundles.
   *
   * <ul>
   *   <li>No entry means the bundle does not have processor extenders.</li>
   *   <li>{@code unrestricted_ == true} means all registered provider bundles
   *       are visible for every Service Type.</li>
   *   <li>{@code unrestricted_ == false} with no provider bundle IDs for a
   *       Service Type means requirements exist but no provider wire resolved
   *       for that Service Type.</li>
   *   <li>{@code unrestricted_ == false} with provider bundle IDs for a
   *       Service Type means only those provider bundles are visible for that
   *       Service Type.</li>
   * </ul>
   */
  private final ConcurrentMap<Bundle, ConsumerVisibility> consumerRequirements_ =
      new ConcurrentHashMap<>();

  private ProviderBundleLifecycleManager providerBundleLifecycleManager_;

  /*---------------------------- methods ------------------------------*/
  /**
   * <p>This method initializes the activator with the given bundle context and
   * starts the bundle trackers used to discover provider and consumer bundles.</p>
   *
   * <p>The provider tracker observes bundles in the {@code STARTING} and
   * {@code ACTIVE} states, so provider bundles can be inspected and registered
   * once they are starting or running.</p>
   *
   * <p>The consumer tracker observes bundles in the {@code RESOLVED},
   * {@code STARTING}, and {@code ACTIVE} states, so consumer
   * weaving metadata can be collected before consumer classes are loaded.</p>
   *
   * @param context the bundle context.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public synchronized void start(BundleContext context) throws Exception
  {
    bundleContext_ = context;
    providerBundleLifecycleManager_ =
        new ProviderBundleLifecycleManager(context,
                                           () -> logger_,
                                           MediatorActivator::printDebug);
    activator_ = this;
    loggerFactoryTracker_ = new ServiceTracker<>(context, LoggerFactory.class,
        new ServiceTrackerCustomizer<LoggerFactory, LoggerFactory>()
        {
          @Override
          public LoggerFactory addingService(
              ServiceReference<LoggerFactory> reference)
          {
            LoggerFactory factory = context.getService(reference);
            if (factory != null)
              {
                logger_ = getLogger(factory);
              }
            return factory;
          }

          @Override
          public void modifiedService(ServiceReference<LoggerFactory> reference,
                                      LoggerFactory factory)
          {
            logger_ = getLogger(factory);
          }

          @Override
          public void removedService(ServiceReference<LoggerFactory> reference,
                                     LoggerFactory factory)
          {
            try
            {
              context.ungetService(reference);
            }
            finally
            {
              logger_ = NO_OP_LOGGER; // or the next available factory logger
            }
          }
        });
    loggerFactoryTracker_.open();

    WeavingHook wh = new ServiceLoaderWeavingHook(this);
    weavingHookService_ = context.registerService(WeavingHook.class.getName(),
                                                  wh, null);

    providerBundleTracker_ = new BundleTracker(context,
                                               PROVIDER_TRACKED_STATES,
                                               new ServiceLoaderProviderTracker(this,
                                                                                context.getBundle()));
    providerBundleTracker_.open();

    consumerBundleTracker_ = new BundleTracker(context,
                                               CONSUMER_TRACKED_STATES,
                                               new ServiceLoaderConsumerTracker(this));
    consumerBundleTracker_.open();
  }

  @Override
  public void stop(BundleContext context)
  {
    // protects against an unusual overlapping restart.
    if (activator_ == this)
    {
      activator_ = null;
    }
    consumerBundleTracker_.close();
    providerBundleTracker_.close();
    providerBundleLifecycleManager_.close();
    weavingHookService_.unregister();
    loggerFactoryTracker_.close();
    logger_ = NO_OP_LOGGER;
  }

  /**
   * Creates the fallback logger used while no OSGi {@link LoggerFactory} is
   * available and during mediator shutdown.
   *
   * <p>The returned logger is intentionally silent: its enabled checks return
   * {@code false}, its logging methods do nothing, and {@link Logger#getName()}
   * returns {@code "noop"}. This lets mediator code log without null checks.</p>
   *
   * @return a no-op logger.
   */
  private static Logger createNoOpLogger()
  {
    return (Logger)Proxy.newProxyInstance(
        Logger.class.getClassLoader(),
        new Class<?>[] { Logger.class },
        (proxy, method, args) ->
        {
          if (method.getName().equals("getName"))
            {
              return "noop";
            }
          if (method.getReturnType() == boolean.class)
            {
              return false;
            }
          if (method.getReturnType() == String.class)
            {
              return "";
            }
          return null;
        });
  }

  private static Logger getLogger(LoggerFactory factory)
  {
    try
      {
        return Objects.requireNonNullElse(
            factory.getLogger(MediatorActivator.class), NO_OP_LOGGER);
      }
    catch (RuntimeException e)
      {
        return NO_OP_LOGGER;
      }
  }

  public void unregisterConsumerBundle(Bundle bundle)
  {
    consumerRequirements_.remove(bundle);
    providerBundleLifecycleManager_.removeConsumer(bundle);
  }

  /**
   * Registers a consumer and, for standard OSGi metadata, captures
   * the provider bundles selected by its resolved service-loader wires.
   *
   * @param bundle the consumer host bundle.
   * @param visibility the provider visibility selected for the consumer.
   */
  public void registerConsumerBundle(Bundle bundle,
                                     ConsumerVisibility visibility)
  {
    Objects.requireNonNull(bundle, "bundle");
    Objects.requireNonNull(visibility, "visibility");
    consumerRequirements_.put(bundle, visibility);
  }

  /**
   * Returns {@code true} if this bundle require ServiceLoader processing.
   */
  public boolean requiresProcessing(Bundle bundle)
  {
    return consumerRequirements_.containsKey(bundle);
  }


  public void registerServiceproviderCapabilities(Bundle providerBundle,
                                                  ProviderCapability capability)
  {
    Objects.requireNonNull(providerBundle, "providerBundle");

    serviceLoaderCapabilities_.
      compute(capability.getServiceType(),
              (ignored, existingCaps) ->
                {
                  Map<Long, List<ProviderCapability>> updatedCaps =
                     existingCaps == null ? new HashMap<>() : existingCaps;

                  updatedCaps.compute(providerBundle.getBundleId(),
                                      (ignored2, caps) ->
                                        {
                                          List<ProviderCapability> updatedSet =
                                          caps == null ? new java.util.ArrayList<>()
                                                       : caps;
                                          updatedSet.add(capability);
                                          return updatedSet;
                                      });
                  return updatedCaps;
                });
  }

  public void registerServiceProviderEntries(Bundle providerBundle,
                                             ProviderEntry entry)
  {
    Objects.requireNonNull(providerBundle, "providerBundle");
    Objects.requireNonNull(entry, "entry");

    serviceLoaderEntries_.
      compute(entry.serviceType(),
              (ignored, existingEntries) ->
                {
                  Map<Long, ProviderEntry> updatedEntries =
                    existingEntries == null ? new HashMap<>() : existingEntries;

                  updatedEntries.put(providerBundle.getBundleId(), entry);

                  logger_.info(String.format("Registered provider bundle %d of service Type  %s",
                              providerBundle.getBundleId(),
                              entry.serviceType()));
                  printDebug(String.format("Registered provider bundle %d of service Type  %s",
                              providerBundle.getBundleId(),
                              entry.serviceType()));
                  return updatedEntries;
                });
  }

  /**
   * Records that a consumer bundle has prepared a provider successfully.
   *
   * <p>The dependency is deliberately retained until
   * {@link #unregisterConsumerBundle(Bundle)}. An iterator can return the
   * prepared provider from {@code next()}, after which consumer code can retain
   * it without further mediator callbacks.</p>
   *
   * @param provider the bundle that supplies the prepared provider.
   * @param consumer the bundle that prepared the provider.
   */
  public void addDependency(Bundle provider, Bundle consumer)
  {
    providerBundleLifecycleManager_.addDependency(provider, consumer);
  }

  /**
   * Removes all Service Provider registrations associated with a bundle.
   *
   * <p>Any pending delayed-stop request is cancelled because it belongs to the
   * provider registration being removed. This releases references and prevents
   * work from an old registration from surviving an update, uninstall, or later
   * re-registration of the same bundle. Removing the dependency state would
   * also cause a stale task's final idle check to fail, but cancelling it here
   * eagerly removes the obsolete task from the scheduler.</p>
   *
   * @param bundle the Provider bundle to remove.
   * @return {@code true} if the bundle was registered as a provider,
   *        {@code false} otherwise.
   */
  public boolean unregisterProviderBundle(Bundle bundle)
  {
    Objects.requireNonNull(bundle, "bundle");
    long bundleId = bundle.getBundleId();
    AtomicBoolean result = new AtomicBoolean();

    serviceLoaderCapabilities_.forEach((serviceType, capsByBundle) -> {
      if (capsByBundle.remove(bundleId) != null)
        {
          result.set(true);
          logger_.info(String.format("Unregistered provider bundle %d for service Type  %s",
                      bundleId, serviceType));
          printDebug("Unregistered provider bundle " + bundleId +
                     " for service type " + serviceType);
        }
    });
    serviceLoaderEntries_.forEach((serviceType, entriesByBundle) -> {
      if (entriesByBundle.remove(bundleId) != null)
        {
          result.set(true);
        }
    });

    if (result.get())
      {
        logger_.info(String.format("Unregistered provider bundle %d ", bundleId));
      }
    providerBundleLifecycleManager_.removeProvider(bundle);
    return result.get();
  }

  public void getProviders(String serviceType,
                           Bundle consumerBundle,
                           Map<Long, Set<String>> result)
  {
    Objects.requireNonNull(serviceType, "serviceType");
    Objects.requireNonNull(consumerBundle, "consumerBundle");
    Objects.requireNonNull(result, "result");

    result.clear();
    Map<Long, ProviderEntry> entries = serviceLoaderEntries_.get(serviceType);
    if (entries == null || entries.isEmpty())
      {
        return;
      }

    ConsumerVisibility visibility =
      consumerRequirements_.get(consumerBundle);

    if (visibility == null)
      {
        // Metadata-free Consumer: every scanned ProviderEntry is a candidate.
        addCompatibleProviders(entries,
                               entries.keySet(),
                               serviceType,
                               consumerBundle,
                               result);
        return;
      }

    // Standard Service Loader Mediator Consumer: only bundles that publish an
    // osgi.serviceloader capability for this Service Type are candidates.
    Map<Long, List<ProviderCapability>> capabilities =
      serviceLoaderCapabilities_.get(serviceType);

    if (capabilities == null || capabilities.isEmpty())
      {
        return;
      }

    Set<Long> candidateBundleIds = new HashSet<>();
    for (Long bundleId : capabilities.keySet())
      {
        if (visibility.allows(serviceType, bundleId.longValue()))
          {
            candidateBundleIds.add(bundleId);
          }
      }
    addCompatibleProviders(entries,
                           candidateBundleIds,
                           serviceType,
                           consumerBundle,
                           result);
  }

  private static void addCompatibleProviders(Map<Long, ProviderEntry> entries,
                                             Set<Long> candidateBundleIds,
                                             String serviceType,
                                             Bundle consumerBundle,
                                             Map<Long, Set<String>> result)
  {
    String packageName = ProviderEntry.packageOf(serviceType);
    BundleCapability consumerPackageCapability =
      ProviderEntry.packageCapability(consumerBundle, packageName);

    for (Long bundleId : candidateBundleIds)
      {
        ProviderEntry entry = entries.get(bundleId);
        if (entry != null &&
            ProviderEntry.sameCapability(entry.packageCapability(),
                                         consumerPackageCapability))
          {
            result.put(bundleId, entry.implementationClasses());
          }
      }
  }

  public Bundle getBundle(long bundleId)
  {
    return bundleContext_.getBundle(bundleId);
  }

  Bundle getMediatorBundle()
  {
    return bundleContext_ == null ? null : bundleContext_.getBundle();
  }

  // for internal debug.
  public static void printDebug(String s)
  {
    if (DEBUG)
      {
        System.out.println(s);
      }
  }

}
