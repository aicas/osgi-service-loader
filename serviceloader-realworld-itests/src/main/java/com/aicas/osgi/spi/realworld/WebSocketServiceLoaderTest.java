/**
 * Copyright (c) 2026 Data In Motion and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package com.aicas.osgi.spi.realworld;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.wiring.BundleWiring;

import com.aicas.osgi.spi.realworld.support.ControlledByKnownFailures;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

/**
 * Verifies discovery of a Tyrus ContainerProvider declared through
 * {@code module-info.class}.
 */
@ControlledByKnownFailures
public class WebSocketServiceLoaderTest
{
  private static final String TYRUS_GRIZZLY_CLIENT_BSN =
      "org.glassfish.tyrus.container-grizzly-client";

  @Test
  void containerProviderIsFound()
  {
    WebSocketContainer container =
        withTyrusContextClassLoader(ContainerProvider::getWebSocketContainer);

    assertThat(container).isNotNull();
    assertThat(container.getClass().getName()).startsWith("org.glassfish.tyrus");
  }

  /**
   * Runs a call with the Tyrus Grizzly client Bundle loader as TCCL.
   *
   * <p>The ServiceLoader mediator supplies the ContainerProvider. After that,
   * Tyrus loads its concrete client container by class name using the TCCL, so
   * the provider Bundle's loader must be visible during provider
   * initialization.</p>
   */
  private <T> T withTyrusContextClassLoader(Supplier<T> call)
  {
    BundleContext context = testBundleContext();
    Bundle grizzlyClient = Arrays.stream(context.getBundles())
        .filter(bundle ->
            TYRUS_GRIZZLY_CLIENT_BSN.equals(bundle.getSymbolicName()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "Tyrus Grizzly client Bundle is not installed"));

    BundleWiring wiring = grizzlyClient.adapt(BundleWiring.class);
    if (wiring == null)
      {
        throw new IllegalStateException(
            "Tyrus Grizzly client Bundle has no wiring");
      }

    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    thread.setContextClassLoader(wiring.getClassLoader());
    try
      {
        return call.get();
      }
    finally
      {
        thread.setContextClassLoader(previous);
      }
  }

  private BundleContext testBundleContext()
  {
    Bundle testBundle = FrameworkUtil.getBundle(getClass());
    if (testBundle == null || testBundle.getBundleContext() == null)
      {
        throw new IllegalStateException(
            "Test is not executing in an active OSGi Bundle");
      }
    return testBundle.getBundleContext();
  }
}
