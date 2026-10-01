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

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamReader;

import org.junit.jupiter.api.Test;

import com.aicas.osgi.spi.realworld.support.ControlledByKnownFailures;

/**
 * Verifies JDK StAX factory discovery of Woodstox through the thread context
 * class loader.
 *
 * <p>{@code XMLInputFactory.newInstance()} runs the JDK's
 * {@code FactoryFinder}, whose ServiceLoader call cannot be woven. The
 * mediator currently has no registry-aware TCCL bridge, so this scenario is a
 * known failure until that bridge is implemented.</p>
 */
@ControlledByKnownFailures
public class JdkFactoryServiceLoaderTest
{
  @Test
  void staxFactoriesComeFromWoodstoxThroughTheTccl() throws Exception
  {
    XMLInputFactory input = XMLInputFactory.newInstance();
    XMLOutputFactory output = XMLOutputFactory.newInstance();

    assertThat(input.getClass().getName())
        .isEqualTo("com.ctc.wstx.stax.WstxInputFactory");
    assertThat(output.getClass().getName())
        .isEqualTo("com.ctc.wstx.stax.WstxOutputFactory");

    XMLStreamReader reader = input.createXMLStreamReader(
        new StringReader("<note>hello osgi</note>"));
    reader.nextTag();
    assertThat(reader.getLocalName()).isEqualTo("note");
    assertThat(reader.getElementText()).isEqualTo("hello osgi");
  }
}
