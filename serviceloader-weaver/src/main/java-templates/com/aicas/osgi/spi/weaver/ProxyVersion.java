/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.weaver;

/** Shared weaver and proxy API version generated from the parent POM during the build. */
public final class ProxyVersion
{
  /** Version of the proxy API targeted by the weaver. */
  public static final String VERSION = "${serviceloader.api.version}";

  /** Exclusive upper bound for compatible proxy API versions. */
  public static final String NEXT_MAJOR_VERSION =
    Math.incrementExact(Integer.parseInt(VERSION.split("\\.", 2)[0])) + ".0.0";

  private ProxyVersion()
  {
  }
}
