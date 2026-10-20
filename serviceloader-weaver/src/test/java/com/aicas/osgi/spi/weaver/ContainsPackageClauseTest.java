/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.weaver;

import static com.aicas.osgi.spi.weaver.WeaverConstants.PROXY_PACKAGE;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Verifies exact package-clause lookup in valid import headers. */
@RunWith(Parameterized.class)
public class ContainsPackageClauseTest
{
  private final String header;
  private final boolean expected;

  public ContainsPackageClauseTest(String header, boolean expected)
  {
    this.header = header;
    this.expected = expected;
  }

  @Parameters(name = "{index}: {0} -> {1}")
  public static Object[][] headers()
  {
    return new Object[][] {
        { "", false },
        { "   ", false },
        { PROXY_PACKAGE, true },
        { PROXY_PACKAGE + ";version=\"[1.0,2)\"", true },
        { PROXY_PACKAGE + ",org.example.api", true },
        { "org.example.api," + PROXY_PACKAGE + ",org.example.other", true },
        { "org.example.api," + PROXY_PACKAGE, true },
        { "org.example.api;version=\"[2,3)\", " + PROXY_PACKAGE +
          ";version=\"[1.0,2)\"", true },
        { "  " + PROXY_PACKAGE + " ;version=\"[1.0,2)\"  ", true },
        { "org.example.api;version=\"[2,3)\",org.example.other", false },
        { PROXY_PACKAGE + ".extra;version=\"[1.0,2)\"", false },
        { PROXY_PACKAGE + "ish", false }
    };
  }

  @Test
  public void containsPackageClauseMatchesExpectedResult()
  {
    boolean actual = Main.containsPackageClause(header, PROXY_PACKAGE);

    assertEquals("Header: " + header, expected, actual);
  }
}
