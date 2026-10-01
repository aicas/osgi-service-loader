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

import org.junit.jupiter.api.Test;

import com.aicas.osgi.spi.realworld.support.ControlledByKnownFailures;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.spi.JsonbProvider;
import jakarta.json.spi.JsonProvider;

/**
 * JSON-P discovers Parsson through {@code ServiceLoader}; JSON-B discovers
 * Yasson, whose provider performs the nested JSON-P discovery.
 */
@ControlledByKnownFailures
public class JsonServiceLoaderTest
{
  public static class Note
  {
    public String text = "hello osgi";
  }

  @Test
  void jsonpProviderIsFoundThroughServiceLoader()
  {
    JsonProvider provider = JsonProvider.provider();

    assertThat(provider.getClass().getName()).startsWith("org.eclipse.parsson");
    JsonObject object = Json.createObjectBuilder().add("text", "hello osgi")
        .build();
    assertThat(object.toString()).isEqualTo("{\"text\":\"hello osgi\"}");
  }

  @Test
  void jsonbProviderIsFoundThroughServiceLoaderAndUsesJsonp() throws Exception
  {
    JsonbProvider provider = JsonbProvider.provider();
    assertThat(provider.getClass().getName()).startsWith("org.eclipse.yasson");

    try (Jsonb jsonb = JsonbBuilder.create())
      {
        String json = jsonb.toJson(new Note());
        assertThat(json).isEqualTo("{\"text\":\"hello osgi\"}");
        assertThat(jsonb.fromJson(json, Note.class).text).isEqualTo("hello osgi");
      }
  }
}
