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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aicas.osgi.spi.realworld.support.ControlledByKnownFailures;

import jakarta.persistence.spi.PersistenceProvider;
import jakarta.persistence.spi.PersistenceProviderResolverHolder;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.RuntimeDelegate;

/**
 * Verifies ServiceLoader-based provider discovery in Jakarta REST and Jakarta
 * Persistence APIs that use the thread context class loader.
 */
@ControlledByKnownFailures
public class RestAndPersistenceServiceLoaderTest
{
  @Test
  void restRuntimeDelegateComesFromJersey()
  {
    RuntimeDelegate delegate = RuntimeDelegate.getInstance();

    assertThat(delegate.getClass().getName()).startsWith("org.glassfish.jersey");
    assertThat(Response.ok("hello osgi").build().getStatus()).isEqualTo(200);
    assertThat(UriBuilder.fromPath("/greeter/{lang}").build("de").toString())
        .isEqualTo("/greeter/de");
  }

  /**
   * Retained for future requirement review.
   *
   * <p>Jakarta Persistence supplies an explicit application/TCCL loader. R11
   * currently requires that a loader other than the consumer Bundle's own
   * loader use ordinary Java ServiceLoader behavior, so expecting mediation of
   * EclipseLink here is not a valid success expectation under the current
   * requirement.</p>
   */
  @Test
  void persistenceProviderResolverFindsEclipseLink()
  {
    List<PersistenceProvider> providers = PersistenceProviderResolverHolder
        .getPersistenceProviderResolver().getPersistenceProviders();

    assertThat(providers).extracting(provider -> provider.getClass().getName())
        .containsExactly("org.eclipse.persistence.jpa.PersistenceProvider");
  }
}
