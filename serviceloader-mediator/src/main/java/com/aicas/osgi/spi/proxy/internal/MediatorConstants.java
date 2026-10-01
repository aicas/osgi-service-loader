/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

public interface MediatorConstants
{
  String EXTENDER_CAPABILITY_NAMESPACE = "osgi.extender";
  String FILTER_DIRECTIVE = "filter";

  // ServiceLoader capability and related directive
  String SERVICELOADER_CAPABILITY_NAMESPACE = "osgi.serviceloader";
  String REGISTER_DIRECTIVE = "register";

  // Service registration property
  String SERVICELOADER_MEDIATOR_PROPERTY = "serviceloader.mediator";

  /** Framework property that configures the idle provider stop delay in milliseconds. */
  String PROVIDER_STOP_DELAY_MILLIS_PROPERTY =
    "com.aicas.osgi.spi.provider.stop.delay.millis";

  // The names of the extenders involved
  String PROCESSOR_EXTENDER_NAME = "osgi.serviceloader.processor";
  String REGISTRAR_EXTENDER_NAME = "osgi.serviceloader.registrar";

  String METAINF_SERVICES = "META-INF/services";
  String MODULE_INFO = "module-info.class";
}
