/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.weaver;

/** Accepted values of the static weaving manifest header. */
public enum StaticWeavingStatus
{
  /** Processing completed with at least one transformed class. */
  WOVEN("woven"),

  /** Scanning completed without transforming any class. */
  SCANNED("scanned");

  private final String value;

  StaticWeavingStatus(String value)
  {
    this.value = value;
  }

  /**
   * Returns the exact value written to the manifest.
   *
   * @return lowercase manifest value.
   */
  public String getValue()
  {
    return value;
  }

  /**
   * Tests whether a value exactly matches an accepted status.
   *
   * @param value value to check; may be {@code null}.
   * @return {@code true} if value is valid; null,
   *         different case, whitespace, and other values are rejected.
   */
  public static boolean isValid(String value)
  {
    for (StaticWeavingStatus status : values())
      {
        if (status.value.equals(value))
          {
            return true;
          }
      }
    return false;
  }
}
