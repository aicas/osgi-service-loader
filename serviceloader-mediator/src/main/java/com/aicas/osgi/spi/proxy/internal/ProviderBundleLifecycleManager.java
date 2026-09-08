/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.log.Logger;

/**
 * Tracks consumer dependencies of provider Bundles and stops an idle provider
 * after a configurable grace period.
 *
 * <p>A dependency begins when a provider has been prepared for a provider and
 * lasts until the consumer Bundle is removed. This deliberately tracks the
 * consumer Bundle lifetime rather than individual provider instances: a provider
 * returned by {@code ServiceLoader.next()} can be retained after the mediator
 * has no further callbacks for that instance.</p>
 *
 * <p>When the final dependency is removed, this manager schedules a delayed
 * stop. A later dependency cancels that stop, and a provider removal discards
 * both its dependencies and any scheduled stop. The manager serializes these
 * transitions with scheduler shutdown, so a stale delayed task cannot stop a
 * provider after it has become active again or been unregistered.</p>
 */
final class ProviderBundleLifecycleManager implements AutoCloseable
{
  /** Grace period after the final consumer dependency is removed. */
  private static final long DEFAULT_PROVIDER_STOP_DELAY_MILLIS = 50_000L;

  /*----------------------- classes and enums -------------------------*/

  /**
   * A delayed-stop request that can be cancelled or superseded.
   *
   * <p>Its identity represents the idle transition that created it. The
   * scheduled task compares that identity with the current request before
   * stopping a provider.</p>
   */
  private static final class PendingProviderStop
  {
    private ScheduledFuture<?> future_;
  }

  /*--------------------------- variables -----------------------------*/

  private final ConcurrentMap<Bundle, Set<Bundle>>
    activeDependencies_ = new ConcurrentHashMap<>();
  private final Object providerStopLock_ = new Object();
  private final ConcurrentMap<Bundle, PendingProviderStop>
    pendingProviderStops_ = new ConcurrentHashMap<>();
  private final long providerStopDelayMillis_;
  private final Supplier<Logger> loggerSupplier_;
  private final Consumer<String> debugOutput_;

  private ScheduledExecutorService providerStopExecutor_;
  private boolean stopping_;

  /*------------------------  constructors  ---------------------------*/

  /**
   * Creates a lifecycle manager using the idle-provider delay configured in
   * the framework properties.
   *
   * @param context the mediator Bundle context used to read the delay property.
   * @param loggerSupplier supplies the current mediator logger for stop errors.
   * @param debugOutput receives optional lifecycle debug messages.
   */
  ProviderBundleLifecycleManager(BundleContext context,
                                 Supplier<Logger> loggerSupplier,
                                 Consumer<String> debugOutput)
  {
    this(providerStopDelayMillis(context), loggerSupplier, debugOutput);
  }

  /**
   * Creates a lifecycle manager with an explicit idle-provider delay.
   *
   * <p>This constructor supports deterministic lifecycle tests without an
   * OSGi framework property lookup.</p>
   *
   * @param providerStopDelayMillis delay between a provider becoming idle and
   *        its stop attempt.
   * @param loggerSupplier supplies the current mediator logger for stop errors.
   * @param debugOutput receives optional lifecycle debug messages.
   */
  ProviderBundleLifecycleManager(long providerStopDelayMillis,
                                 Supplier<Logger> loggerSupplier,
                                 Consumer<String> debugOutput)
  {
    this.providerStopDelayMillis_ = providerStopDelayMillis;
    this.loggerSupplier_ = loggerSupplier;
    this.debugOutput_ = debugOutput;
  }

  /*---------------------------- methods ------------------------------*/

  /**
   * Records that a consumer Bundle has prepared a provider Bundle.
   *
   * <p>A new dependency cancels any pending stop for the provider. The
   * dependency remains until {@link #removeConsumer(Bundle)} is called.</p>
   *
   * @param provider the Bundle that supplies the prepared provider.
   * @param consumer the Bundle that prepared the provider.
   */
  void addDependency(Bundle provider, Bundle consumer)
  {
    synchronized (providerStopLock_)
      {
        cancelPendingProviderStop(provider);
        activeDependencies_.compute(provider,
                                    (ignored, consumers) ->
                                      {
                                        Set<Bundle> updatedConsumers =
                                        consumers == null ? ConcurrentHashMap.newKeySet() :
                                                            consumers;
                                        updatedConsumers.add(consumer);
                                        return updatedConsumers;
                                      });
      }
    debugOutput_.accept("ADD DEP " + provider.getSymbolicName() + " - " +
                        consumer.getSymbolicName());
  }

  /**
   * Removes a consumer Bundle from every provider it prepared.
   *
   * <p>When the removal leaves a provider with no dependencies, a delayed stop
   * is scheduled. This method does not stop a provider synchronously.</p>
   *
   * @param consumer the removed consumer Bundle.
   */
  void removeConsumer(Bundle consumer)
  {
    synchronized (providerStopLock_)
      {
        activeDependencies_.forEach((provider, consumers) ->
        {
          if (consumers.remove(consumer) && consumers.isEmpty())
            {
              scheduleProviderStop(provider);
            }
        });
      }
  }

  /**
   * Removes lifecycle state for an unregistered provider Bundle.
   *
   * <p>Any pending delayed stop is cancelled so a task from an old provider
   * registration cannot run after an update, uninstall, or re-registration.</p>
   *
   * @param provider the removed provider Bundle.
   */
  void removeProvider(Bundle provider)
  {
    synchronized (providerStopLock_)
      {
        activeDependencies_.remove(provider);
        cancelPendingProviderStop(provider);
      }
  }

  /**
   * Cancels pending provider stops and shuts down the delayed-stop executor.
   *
   * <p>After this method returns, the manager does not schedule or execute new
   * provider-stop work.</p>
   */
  @Override
  public void close()
  {
    synchronized (providerStopLock_)
      {
        stopping_ = true;
        pendingProviderStops_.forEach((provider,
                                       pendingStop) ->
        {
          if (pendingStop.future_ != null)
            {
              pendingStop.future_.cancel(false);
            }
        });
        pendingProviderStops_.clear();
        if (providerStopExecutor_ != null)
          {
            providerStopExecutor_.shutdownNow();
            providerStopExecutor_ = null;
          }
      }
  }

  /**
   * Schedules a stop for a provider that remains idle after the grace period.
   *
   * <p>The caller holds {@link #providerStopLock_}. Replacing any existing
   * request ensures that only the latest idle transition can stop the
   * provider.</p>
   *
   * @param provider the idle provider Bundle.
   */
  private void scheduleProviderStop(Bundle provider)
  {
    if (stopping_ || !isProviderIdle(provider))
      {
        return;
      }
    cancelPendingProviderStop(provider);
    PendingProviderStop pendingStop = new PendingProviderStop();
    pendingProviderStops_.put(provider, pendingStop);
    try
      {
        pendingStop.future_ =
            getProviderStopExecutor().
            schedule(() -> stopProviderIfStillIdle(provider, pendingStop),
                     providerStopDelayMillis_,
                     TimeUnit.MILLISECONDS);
      }
    catch (RejectedExecutionException e)
      {
        pendingProviderStops_.remove(provider, pendingStop);
        return;
      }

    debugOutput_.accept("[STOPPING]" + provider.getSymbolicName() +
                        " has no consumers, will be stopped");
  }

  /**
   * Stops a provider only when the scheduled request is still current and the
   * provider still has no consumer dependencies.
   *
   * @param provider the provider Bundle to inspect.
   * @param pendingStop the request captured when this task was scheduled.
   */
  private void stopProviderIfStillIdle(Bundle provider,
                                       PendingProviderStop pendingStop)
  {
    synchronized (providerStopLock_)
      {
        PendingProviderStop currentStop = pendingProviderStops_.get(provider);
        if (stopping_ ||
            currentStop != pendingStop ||
            !pendingProviderStops_.remove(provider, pendingStop) ||
            !isProviderIdle(provider))
          {
            return;
          }
      }

    try
      {
        provider.stop();
        debugOutput_.accept("[STOPPED]" + provider.getSymbolicName());
      }
    catch (BundleException e)
      {
        loggerSupplier_.get().warn("Could not stop provider bundle " +
                                   provider.getSymbolicName(), e);
      }
  }

  /**
   * Reports whether a provider has an empty dependency set.
   *
   * <p>The caller holds {@link #providerStopLock_}.</p>
   *
   * @param provider the provider Bundle to inspect.
   * @return {@code true} when the provider remains known and has no consumers.
   */
  private boolean isProviderIdle(Bundle provider)
  {
    Set<Bundle> consumers = activeDependencies_.get(provider);
    return consumers != null && consumers.isEmpty();
  }

  /**
   * Removes and cancels a provider's current delayed-stop request.
   *
   * <p>Cancellation does not interrupt a running task; the task detects that
   * its request is no longer current before it calls {@link Bundle#stop()}.</p>
   *
   * @param provider the provider whose pending request is cancelled.
   */
  private void cancelPendingProviderStop(Bundle provider)
  {
    PendingProviderStop pendingStop = pendingProviderStops_.remove(provider);
    if (pendingStop != null && pendingStop.future_ != null)
      {
        pendingStop.future_.cancel(false);
      }
  }

  /**
   * Returns the daemon executor for delayed provider stops, creating it on the
   * first scheduled request.
   *
   * <p>Cancelled tasks are removed from its queue immediately.</p>
   *
   * @return the delayed-stop executor.
   */
  private ScheduledExecutorService getProviderStopExecutor()
  {
    if (providerStopExecutor_ == null || providerStopExecutor_.isShutdown())
      {
        ScheduledThreadPoolExecutor executor =
          new ScheduledThreadPoolExecutor(1, runnable ->
          {
            Thread thread = new Thread(runnable,
                                       "ServiceLoader provider shutdown");
            thread.setDaemon(true);
            return thread;
          });
        executor.setRemoveOnCancelPolicy(true);
        providerStopExecutor_ = executor;
      }
    return providerStopExecutor_;
  }

  /**
   * Reads a positive idle-provider stop delay from framework properties.
   *
   * <p>Missing, non-numeric, and non-positive values use
   * {@link #DEFAULT_PROVIDER_STOP_DELAY_MILLIS}.</p>
   *
   * @param context the Bundle context that exposes framework properties.
   * @return the configured delay in milliseconds.
   */
  private static long providerStopDelayMillis(BundleContext context)
  {
    String value =
        context.getProperty(MediatorConstants.PROVIDER_STOP_DELAY_MILLIS_PROPERTY);
    if (value == null)
      {
        return DEFAULT_PROVIDER_STOP_DELAY_MILLIS;
      }
    try
      {
        long delay = Long.parseLong(value);
        return delay > 0 ? delay : DEFAULT_PROVIDER_STOP_DELAY_MILLIS;
      }
    catch (NumberFormatException e)
      {
        return DEFAULT_PROVIDER_STOP_DELAY_MILLIS;
      }
  }
}
