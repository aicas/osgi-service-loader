/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy;

import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.BundleProvider;
import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.TestService;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Iterator;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.aicas.osgi.spi.proxy.internal.MediatedServiceLoaderTestSupport;
import com.aicas.osgi.spi.proxy.internal.MediatedServiceLoaderTestSupport.MediatedFixture;
import com.aicas.osgi.spi.proxy.internal.MediatedServiceLoaderTestSupport.MediatorEnvironment;

/** Tests the mediator-backed path of the combined {@link ServiceLoader}. */
public class MediatorServiceLoaderTest
{
  private MediatorEnvironment mediatorEnvironment_;

  @Before
  public void installTestMediator()
  {
    mediatorEnvironment_ = MediatedServiceLoaderTestSupport.install();
  }

  @After
  public void restoreMediator()
  {
    if (mediatorEnvironment_ != null)
      {
        mediatorEnvironment_.close();
      }
  }

  /**
   * Verifies that mediation obtains the provider class through the provider
   * Bundle wiring rather than the test class path's Java ServiceLoader file.
   */
  @Test
  public void loadsProviderThroughProviderBundleClassLoader()
    throws Exception
  {
    MediatedFixture fixture = mediatorEnvironment_.createFixture(17L);

    TestService provider = fixture.loader().findFirst().orElseThrow();

    assertTrue(provider instanceof BundleProvider);
    assertEquals("BundleProvider", provider.value());
    assertEquals(Set.of(BundleProvider.class.getName()),
                 fixture.requestedProviderClassNames());
  }

  /**
   * Verifies that {@link ServiceLoader#reload()} clears the mediated instance
   * cache and permits a fresh provider instance to be created.
   */
  @Test
  public void reloadReplacesCachedMediatedProvider()
    throws Exception
  {
    MediatedFixture fixture = mediatorEnvironment_.createFixture(18L);
    ServiceLoader<TestService> loader = fixture.loader();

    TestService first = loader.findFirst().orElseThrow();
    assertSame(first, loader.findFirst().orElseThrow());

    loader.reload();

    assertNotSame(first, loader.findFirst().orElseThrow());
  }

  /**
   * Verifies that a provider prepared by {@link Iterator#hasNext()} remains
   * returnable when its Bundle stops before {@link Iterator#next()}.
   */
  @Test
  public void returnsProviderPreparedBeforeItsBundleStops()
    throws Exception
  {
    MediatedFixture fixture = mediatorEnvironment_.createFixture(19L);
    Iterator<TestService> providers = fixture.loader().iterator();

    assertTrue(providers.hasNext());
    fixture.stopProviderBundle();

    TestService provider = providers.next();

    assertTrue(provider instanceof BundleProvider);
    assertEquals("BundleProvider", provider.value());
    verify(fixture.providerBundle(), never()).start();
  }

  /**
   * Verifies that registering another Service Type does not evict a provider
   * already cached for this loader's requested Service Type.
   */
  @Test
  public void unrelatedProviderRegistrationDoesNotEvictCachedProvider()
    throws Exception
  {
    MediatedFixture fixture = mediatorEnvironment_.createFixture(20L);
    ServiceLoader<TestService> loader = fixture.loader();
    TestService cachedProvider = loader.findFirst().orElseThrow();

    fixture.registerUnrelatedProvider();

    assertSame(cachedProvider, loader.findFirst().orElseThrow());
  }
}
