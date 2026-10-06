/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/** Test fixture representing an active mediator with its own bundle context. */
final class ActiveMediatorTestFixture
{
  private final MediatorActivator activator_ = new MediatorActivator();
  private final Bundle mediatorBundle_ = mock(Bundle.class);
  private final BundleContext context_ = mock(BundleContext.class);

  private ActiveMediatorTestFixture()
  {
    when(context_.getBundle()).thenReturn(mediatorBundle_);
    setMediatorField(activator_, "bundleContext_", context_);
  }

  static ActiveMediatorTestFixture create()
  {
    return new ActiveMediatorTestFixture();
  }

  MediatorActivator activator()
  {
    return activator_;
  }

  Bundle mediatorBundle()
  {
    return mediatorBundle_;
  }

  BundleContext context()
  {
    return context_;
  }

  static void setMediatorField(MediatorActivator activator,
                               String fieldName,
                               Object value)
  {
    try
      {
        Field field = MediatorActivator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(activator, value);
      }
    catch (ReflectiveOperationException e)
      {
        throw new AssertionError("Could not set mediator field " + fieldName,
                                 e);
      }
  }
}
