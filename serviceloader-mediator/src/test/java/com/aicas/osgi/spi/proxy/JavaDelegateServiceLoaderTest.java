/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.Test;

import com.aicas.osgi.spi.proxy.internal.MediatorActivator;

import static com.aicas.osgi.spi.proxy.ServiceLoaderTestSupport.*;
import static org.junit.Assert.*;

/**
 * Unit tests for the Java-delegate path of the combined ServiceLoader.
 *
 * <p>The regular test class loader has a {@code META-INF/services} resource
 * declaring {@link JavaProvider} for {@link TestService}, but intentionally
 * has no {@code META-INF/services/java.lang.Runnable} resource. Java-delegate
 * lookups therefore find {@code JavaProvider} for {@code TestService} and no
 * provider for {@link Runnable}.</p>
 *
 * <p>The suite verifies the public Java-delegate API: default and explicit
 * class-loader discovery, provider caching and {@link ServiceLoader#reload()},
 * empty lookup and iterator exhaustion, and null class-loader rejection.</p>
 *
 * <p>These tests are not bundle-backed and normally start no
 * {@link MediatorActivator}.</p>
 */
public class JavaDelegateServiceLoaderTest
{
  @Test
  public void loadUsesTheJavaDelegateWhenMediatorIsUnavailable()
  {
    assertNull(MediatorActivator.activator_);

    ServiceLoader<TestService> loader = ServiceLoader.load(TestService.class,
                                                           JavaDelegateServiceLoaderTest.class);
    Iterator<TestService> providers = loader.iterator();

    assertTrue(providers.hasNext());
    assertTrue(providers.next() instanceof JavaProvider);
  }

  @Test
  public void loadWithExplicitClassLoaderUsesThatLoader()
  {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    ServiceLoader<TestService> loader =
      ServiceLoader.load(TestService.class, classLoader,
                         JavaDelegateServiceLoaderTest.class);

    Optional<TestService> provider = loader.findFirst();

    assertTrue(provider.isPresent());
    assertTrue(provider.get() instanceof JavaProvider);
    System.out.println(provider.get().value());
  }

  @Test
  public void reloadCreatesFreshJavaProviderInstances()
  {
    ServiceLoader<TestService> loader = ServiceLoader.load(TestService.class,
                                                           JavaDelegateServiceLoaderTest.class);

    TestService first = loader.findFirst().get();
    loader.reload();
    TestService second = loader.findFirst().get();
    assertNotSame(first, second);
  }

  @Test
  public void getCachedJavaProviderInstances()
  {
    ServiceLoader<TestService> loader = ServiceLoader.load(TestService.class,
                                                           JavaDelegateServiceLoaderTest.class);
    TestService first = loader.findFirst().get();
    TestService second = loader.findFirst().get();
    assertSame(first, second);
  }

  @Test
  public void findFirstReturnsEmptyWhenNoProviderExists()
  {
    ServiceLoader<Runnable> loader = ServiceLoader.load(Runnable.class,
                                                        JavaDelegateServiceLoaderTest.class);
    assertEquals(Optional.empty(), loader.findFirst());
  }

  @Test
  public void iteratorNextThrowsWhenNoProviderRemains()
  {
    ServiceLoader<Runnable> loader = ServiceLoader.load(Runnable.class,
                                                        JavaDelegateServiceLoaderTest.class);
    Iterator<Runnable> iterator = loader.iterator();

    assertFalse(iterator.hasNext());
    try
      {
        iterator.next();
      }
    catch (NoSuchElementException expected)
      {
        return;
      }
    throw new AssertionError("Expected NoSuchElementException");
  }

  @Test
  public void loadWithNullClassLoaderUsesJavaServiceLoaderSemantics()
  {
    ServiceLoader<TestService> loader = ServiceLoader.load(TestService.class,
                                                           null,
                                                           JavaDelegateServiceLoaderTest.class);

    assertNotNull(loader);
  }
}
