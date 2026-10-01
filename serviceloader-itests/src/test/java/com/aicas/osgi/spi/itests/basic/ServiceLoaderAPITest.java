/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.itests.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import com.aicas.osgi.spi.itests.support.AbstractTest;

/** Verifies JDK ServiceLoader stream and explicit-class-loader API paths. */
public class ServiceLoaderAPITest extends AbstractTest
{
  private static final String SUPPORTED_API_CONSUMER =
    "com.aicas.osgi.spi.example.consumer.v2.SupportedApiConsumer";
  private static final String STREAM_CONSUMER =
    "com.aicas.osgi.spi.example.consumer.v2.StreamConsumer";

  private Bundle provider_;
  private Bundle consumer_;

  /** Starts the version 2 provider and consumer Bundles for each test. */
  @Before
  public void startV2Bundles()
    throws Exception
  {
    provider_ = installTestBundle(BUNDLE_PROVIDER_V2);
    consumer_ = installTestBundle(BUNDLE_CONSUMER_V2);

    provider_.start();
    consumer_.start();
  }

  /** Stops the consumer and provider after each test. */
  @After
  public void stopV2Bundles()
    throws Exception
  {
    consumer_.stop();
    provider_.stop();
  }

  /**
   * Verifies that a consumer can execute the standard JDK
   * {@link java.util.ServiceLoader#stream()} path without a weaving error.
   */
  @Test
  public void consumerV2StreamRunsWithoutWeaving()
    throws Exception
  {
    Class<?> streamConsumer = consumer_.loadClass(STREAM_CONSUMER);

    streamConsumer.getMethod("loadFirstProvider").invoke(null);
  }

  /**
   * Verifies R11: an explicit lookup with the consumer Bundle's own class
   * loader remains mediated and discovers the version 2 provider.
   */
  @Test
  public void consumerV2FindsProviderWithOwnBundleClassLoader()
    throws Exception
  {
    Class<?> supportedApiConsumer = consumer_.loadClass(SUPPORTED_API_CONSUMER);
    int outputOffset = outputLength();
    supportedApiConsumer.getMethod("loadServiceWithGivenClassLoader",
                                   ClassLoader.class).
                         invoke(null, supportedApiConsumer.getClassLoader());

    awaitOutputContainsAfter(outputOffset, V2_PROVIDER_MESSAGE);
  }

  /**
   * Verifies R11: a context class loader distinct from the consumer Bundle's
   * class loader uses normal JDK ServiceLoader lookup and finds no provider.
   */
  @Test
  public void consumerV2DoesNotFindProviderWithContextClassLoader()
    throws Exception
  {
    Class<?> supportedApiConsumer = consumer_.loadClass(
                                                        SUPPORTED_API_CONSUMER);
    ClassLoader contextClassLoader =
      Thread.currentThread().getContextClassLoader();
    assertNotSame("Context class loader must not be the consumer Bundle loader",
                  supportedApiConsumer.getClassLoader(), contextClassLoader);

    int outputOffset = outputLength();
    supportedApiConsumer.getMethod("loadServiceWithGivenClassLoader",
                                   ClassLoader.class).
                         invoke(null, contextClassLoader);

    awaitOutputContainsAfter(outputOffset,
                             "[supportedApiConsumer] No provider found.");
  }

  /**
   * Verifies that the supported {@link java.util.ServiceLoader#toString()}
   * invocation is remapped to the mediator proxy.
   */
  @Test
  public void consumerV2ServiceLoaderToStringUsesProxy()
    throws Exception
  {
    Class<?> supportedApiConsumer = consumer_.loadClass(SUPPORTED_API_CONSUMER);

    Object value = supportedApiConsumer.getMethod("serviceLoaderToString",
                                                  ClassLoader.class).
                                        invoke(null, supportedApiConsumer.getClassLoader());

    assertEquals("com.aicas.osgi.spi.proxy.ServiceLoader[" +
                 "com.aicas.osgi.spi.example.spi.SPIProvider]",
                 value);
  }

}
