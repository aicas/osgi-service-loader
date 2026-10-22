/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.weaver;

import java.util.List;

/** Manifest headers and weaving configuration for the ServiceLoader weavers. */
public interface WeaverConstants
{
  /** Manifest header identifying the bundle's symbolic name. */
  String BUNDLE_SYMBOLIC_NAME = "Bundle-SymbolicName";

  /** Manifest header listing the bundle's class-path entries. */
  String BUNDLE_CLASSPATH = "Bundle-ClassPath";

  /** Manifest header declaring imported packages. */
  String IMPORT_PACKAGE = "Import-Package";

  /** Package containing the ServiceLoader proxy used by woven classes. */
  String PROXY_PACKAGE = "com.aicas.osgi.spi.proxy";

  /** Proxy API versions supported by generated calls. */
  String PROXY_VERSION_RANGE =
      "[" + ProxyVersion.VERSION + "," + ProxyVersion.NEXT_MAJOR_VERSION + ")";

  /** Proxy package import clause with the supported version range. */
  String PROXY_IMPORT = PROXY_PACKAGE + ";version=\"" + PROXY_VERSION_RANGE + "\"";

  /**
   * Manifest header recording completed static processing.
   *
   * <p>The value is {@code woven} when a class was transformed and
   * {@code scanned} when scanning completed without a transformation.
   * Values are defined by {@link StaticWeavingStatus}. Valid statuses prevent
   * repeat static processing and runtime weaving; invalid values are rejected.</p>
   */
  String STATIC_WEAVING_HEADER = "Aicas-OSGi-Spi-Weaver";

  /** Bundle symbolic-name prefixes that must not be woven. */
  List<String> INFRASTRUCTURE_BUNDLE_PREFIXES = List.of("org.objectweb.asm",
                                                        "biz.aQute.tester",
                                                        "junit-platform-",
                                                        "junit-jupiter-",
                                                        "org.opentest4j",
                                                        "assertj-core",
                                                        "net.bytebuddy");

  /**
   * Determines whether a symbolic name belongs to a bundle excluded from
   * ServiceLoader weaving.
   *
   * @param symbolicName OSGi bundle symbolic name
   * @return {@code true} when the name matches an infrastructure prefix
   */
  static boolean isInfrastructureBundle(String symbolicName)
  {
    return symbolicName != null &&
           WeaverConstants.INFRASTRUCTURE_BUNDLE_PREFIXES.stream().
                           anyMatch(symbolicName::startsWith);
  }
}
