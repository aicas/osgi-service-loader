/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.log.Logger;

/**
 * Tests delayed provider shutdown after a consumer is removed.
 *
 * <p>Each test uses a mocked provider {@link Bundle}. Mockito verifies when
 * the lifecycle manager calls {@link Bundle#stop()} on that Bundle:
 * {@code after(delay).never()} waits through {@code delay} and proves no stop
 * call occurred, while {@code timeout(delay)} waits up to {@code delay} for
 * the expected stop call.</p>
 */
public class ProviderBundleLifecycleManagerTest
{
  private static final long PROVIDER_STOP_DELAY_MILLIS_TEST = 500L;
  /** Allows for scheduler and test-run timing variation around the grace period. */
  private static final long TOLERANCE = 50L;

  private ProviderBundleLifecycleManager lifecycleManager_;

  @Before
  public void createLifecycleManager()
  {
    lifecycleManager_ =
            new ProviderBundleLifecycleManager(PROVIDER_STOP_DELAY_MILLIS_TEST,
                                               () -> mock(Logger.class), ignored ->{});
  }

  @After
  public void shutDownSchedulers()
  {
    if (lifecycleManager_ != null)
      {
        lifecycleManager_.close();
      }
  }

  /** Verifies that an idle provider is stopped after the grace period. */
  @Test
  public void stopsProviderAfterTheLastConsumerIsRemoved() throws Exception
  {
    Bundle provider = mock(Bundle.class);
    Bundle consumer = mock(Bundle.class);

    lifecycleManager_.addDependency(provider, consumer);
    lifecycleManager_.removeConsumer(consumer);

    // The provider remains running until the grace period has elapsed.
    verify(provider,
           after(PROVIDER_STOP_DELAY_MILLIS_TEST - TOLERANCE).never()).stop();
    // The provider is stopped once the grace period has elapsed.
    verify(provider,
           timeout(PROVIDER_STOP_DELAY_MILLIS_TEST + TOLERANCE)).stop();
  }

  /** Verifies that a returning consumer cancels a pending provider stop. */
  @Test
  public void keepsProviderRunningWhenAnotherConsumerArrivesDuringDelay()
      throws Exception
  {
    Bundle provider = mock(Bundle.class);
    Bundle departingConsumer = mock(Bundle.class);
    Bundle newConsumer = mock(Bundle.class);

    lifecycleManager_.addDependency(provider, departingConsumer);
    lifecycleManager_.removeConsumer(departingConsumer);
    lifecycleManager_.addDependency(provider, newConsumer);

    // The arriving consumer cancels the pending stop.
    verify(provider,
           after(PROVIDER_STOP_DELAY_MILLIS_TEST * 3).never()).stop();
  }

  /** Verifies that removing a provider cancels its pending delayed stop. */
  @Test
  public void doesNotStopProviderRemovedDuringDelay() throws Exception
  {
    Bundle provider = mock(Bundle.class);
    Bundle consumer = mock(Bundle.class);

    lifecycleManager_.addDependency(provider, consumer);
    lifecycleManager_.removeConsumer(consumer);
    lifecycleManager_.removeProvider(provider);

    // Removing the provider cancels its pending stop.
    verify(provider,
           after(PROVIDER_STOP_DELAY_MILLIS_TEST * 2).never()).stop();
  }

  /** Verifies that a new idle transition starts a fresh grace period. */
  @Test
  public void restartsGracePeriodWhenAConsumerReturnsAndLeavesAgain()
      throws Exception
  {
    Bundle provider = mock(Bundle.class);
    Bundle firstConsumer = mock(Bundle.class);
    Bundle secondConsumer = mock(Bundle.class);

    lifecycleManager_.addDependency(provider, firstConsumer);
    lifecycleManager_.removeConsumer(firstConsumer);

    Thread.sleep(PROVIDER_STOP_DELAY_MILLIS_TEST / 3);

    lifecycleManager_.addDependency(provider, secondConsumer);
    Thread.sleep(PROVIDER_STOP_DELAY_MILLIS_TEST * 2 / 3);

    // The second consumer cancels the original pending stop.
    verify(provider, after(TOLERANCE).never()).stop();

    lifecycleManager_.removeConsumer(secondConsumer);
    // The new idle transition does not stop the provider before its grace period.
    verify(provider,
           after(PROVIDER_STOP_DELAY_MILLIS_TEST - TOLERANCE).never()).stop();
    // The provider stops once the new grace period has elapsed.
    verify(provider,
           timeout(PROVIDER_STOP_DELAY_MILLIS_TEST + TOLERANCE)).stop();
  }
}
