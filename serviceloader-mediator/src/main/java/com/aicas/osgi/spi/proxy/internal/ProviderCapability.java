/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/
package com.aicas.osgi.spi.proxy.internal;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import java.util.Hashtable;
import java.util.Map;

/**
 * The ServiceLoaderCapability class represents a capability for an OSGi service
 * loader. It holds information about the service type, implementation, and the
 * underlying bundle capability associated with the service loader.
 */
public class ProviderCapability
{

  /*----------------------- classes and enums -------------------------*/
  enum RegisterMode
  {
    NONE, // No Service Providers are registered as OSGi services.
    ALL, //  All advertised providers of the Service Type are registered.
    SINGLE // Only the specified provider implementation is registered.
  }

  ProviderCapability(String serviceType,
                     BundleCapability serviceLoaderCapability,
                     Bundle bundle)
  {
    this.serviceType_ = serviceType;
    this.serviceLoaderCapability_ = serviceLoaderCapability;
    this.bundle_ = bundle;
  }

  private final String serviceType_;
  private final BundleCapability serviceLoaderCapability_;
  private final Bundle bundle_;

  /*
   * information from the osgi.serviceloader capability. Init only when Osgi
   * service registration is requested for the Service Type.
   * <p>The registration behavior is represented by {@link RegisterMode}:</p>
   *
   * <ul>
   *   <li>{@link RegisterMode#NONE}: no Provider implementation is registered as
   *       an OSGi service.</li>
   *   <li>{@link RegisterMode#ALL}: all Provider implementations associated with
   *       the advertised Service Type are registered.</li>
   *   <li>{@link RegisterMode#SINGLE}: only the Provider implementation named by
   *       {@link #getSelectedProvider()} is registered.</li>
   * </ul>
   *
   * <p>The selectedProvider_ therefore non-{@code null} only when the
   * registration mode is {@link ProviderCapability.RegisterMode#SINGLE}.
   *  For the other modes, it is
   * {@code null}.</p>
   */
  private RegisterMode registerMode_;
  private String selectedProvider_ = null;
  private Hashtable<String, Object> attributes_;

  public BundleCapability getServiceLoaderCapability()
  {
    return serviceLoaderCapability_;
  }

  public String getServiceType()
  {
    return serviceType_;
  }

  public Hashtable<String, Object> getAttributes()
  {
    if (attributes_ == null)
      {
        Hashtable<String, Object> attributes = new Hashtable<String, Object>();
        for (Map.Entry<String, Object> entry :
             serviceLoaderCapability_.getAttributes().entrySet())
          {
            String key = entry.getKey();
            Object value = entry.getValue();
            // ignore the already handled osgi.serviceloader attribute, private attribute, empty attribute.
            if (key.equals(MediatorConstants.SERVICELOADER_CAPABILITY_NAMESPACE) ||
                key.startsWith(".") ||
                value == null)
              {
                continue;
              }
            attributes.put(entry.getKey(), entry.getValue());
            MediatorActivator
                .printDebug("[PROVIDER_PROCESSOR] Provider Metadata " +
                            entry.getKey() + ") - (" + entry.getValue() + ")");
          }
        attributes_ = attributes;
      }
    return new Hashtable<>(attributes_);
  }

  public String getSelectedProvider()
  {
    parseRegisterDirective();
    return selectedProvider_;
  }

  public RegisterMode getRegisterMode()
  {
    parseRegisterDirective();
    return registerMode_;
  }

  private void parseRegisterDirective()
  {
    if (registerMode_ != null)
      {
        return;
      }
    String register = serviceLoaderCapability_.getDirectives()
        .get(MediatorConstants.REGISTER_DIRECTIVE);
    if (register == null)
      {
        registerMode_ = RegisterMode.ALL;
      }
    else
      {
        String selectedProvider = HeaderProcessor.normalize(register);
        if (selectedProvider == null)
          {
            registerMode_ = RegisterMode.NONE;
          }
        else
          {
            registerMode_ = RegisterMode.SINGLE;
          }
        selectedProvider_ = selectedProvider;
      }
  }
}
