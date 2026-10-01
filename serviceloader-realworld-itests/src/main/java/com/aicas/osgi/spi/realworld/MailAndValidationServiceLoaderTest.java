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

import java.util.Properties;
import java.util.Set;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.Test;

import com.aicas.osgi.spi.realworld.support.ControlledByKnownFailures;

import jakarta.mail.Session;
import jakarta.mail.util.StreamProvider;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;

/**
 * Verifies ServiceLoader-based provider discovery for Jakarta Mail and Jakarta
 * Validation using Angus Mail and Hibernate Validator.
 */
@ControlledByKnownFailures
public class MailAndValidationServiceLoaderTest
{
  public static class Note
  {
    @NotNull
    public String text;
  }

  @Test
  void mailStreamProviderAndSessionProvidersComeFromAngus()
  {
    assertThat(StreamProvider.provider().getClass().getName())
        .startsWith("org.eclipse.angus.mail");

    Session session = Session.getInstance(new Properties());

    assertThat(session.getProviders())
        .extracting(provider -> provider.getClassName())
        .anyMatch(name -> name.startsWith("org.eclipse.angus.mail.smtp"))
        .anyMatch(name -> name.startsWith("org.eclipse.angus.mail.imap"));
  }

  @Test
  void validationProviderIsHibernateValidator()
  {
    try (ValidatorFactory factory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory())
      {
        assertThat(factory.getClass().getName())
            .startsWith("org.hibernate.validator");

        Set<ConstraintViolation<Note>> violations =
            factory.getValidator().validate(new Note());

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString())
            .isEqualTo("text");
      }
  }
}
