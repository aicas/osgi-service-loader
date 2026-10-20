/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServicePermission;

/** Internal permission checks used by the Service Loader mediator. */
public final class MediatorSecurity
{
  private MediatorSecurity()
  {
    // utility class
  }

  /** Returns whether a bundle has the requested Service Permission. */
  @SuppressWarnings("removal")
  public static boolean hasServicePermission(Bundle bundle,
                                             String serviceType,
                                             String action)
  {
    if(bundle == null || serviceType == null)
      {
        return false;
      }
    if (System.getSecurityManager() == null)
      {
        return true;
      }
    try
      {
        return bundle.hasPermission(new ServicePermission(serviceType,
                                                          action));
      }
    catch (SecurityException e)
      {
        return false;
      }
  }

  /** Returns whether the mediator may read metadata from the target bundle. */
  public static boolean canReadMetadata(Bundle mediator, Bundle target)
  {
    return hasAdminPermission(mediator, target, AdminPermission.METADATA);
  }

  /** Returns whether the mediator may access the target bundle's context. */
  public static boolean canAccessContext(Bundle mediator, Bundle target)
  {
    return hasAdminPermission(mediator, target, AdminPermission.CONTEXT);
  }

  /** Returns whether the mediator may load classes from the target bundle. */
  public static boolean canLoadClasses(Bundle mediator, Bundle target)
  {
    return hasAdminPermission(mediator, target, AdminPermission.CLASS);
  }

  /** Returns whether the mediator may start or stop the target bundle. */
  public static boolean canExecute(Bundle mediator, Bundle target)
  {
    return hasAdminPermission(mediator, target, AdminPermission.EXECUTE);
  }

  @SuppressWarnings("removal")
  private static boolean hasAdminPermission(Bundle mediator,
                                            Bundle target,
                                            String action)
  {
    if(mediator == null || target == null)
      {
        return false;
      }
    if (System.getSecurityManager() == null)
      {
        return true;
      }
    try
      {
        boolean permitted = mediator.hasPermission(new AdminPermission(target, action));
        if (!permitted)
          {
            MediatorActivator.logger_.warn("Mediator doesn't have AdminPermission[" +
                                           target.getSymbolicName() + "," +
                                           action + "].");
          }
        return permitted;
      }
    catch (SecurityException e)
      {
        MediatorActivator.logger_.warn("Could not check AdminPermission[" +
                                       target.getSymbolicName() + "," +
                                       action + "] for mediator.", e);
        return false;
      }
  }
}
