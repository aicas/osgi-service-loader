/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;

import com.aicas.osgi.spi.weaver.ServiceLoaderWeaver;
import com.aicas.osgi.spi.weaver.StaticWeavingStatus;
import com.aicas.osgi.spi.weaver.WeaverConstants;

public class ServiceLoaderWeavingHook implements WeavingHook
{
  private final MediatorActivator activator_;
  private final Bundle weaverBundle_;

  /**
   * Concurrent cache of wirings found to carry a valid static processing status.
   * Replacement wirings are checked independently, and unmarked wirings are
   * not cached. Entries are held by strong references for this hook's lifetime.
   */
  private final Set<BundleWiring> staticallyProcessedWirings_ =
      ConcurrentHashMap.newKeySet();
  private static final String MEDIATOR_PACKAGE =
      com.aicas.osgi.spi.proxy.ServiceLoader.class.getPackage().getName();

  ServiceLoaderWeavingHook(MediatorActivator act)
  {
    activator_ = act;
    // Resolve this before registering the hook. Resolving the weaver bundle
    // from weave() can otherwise trigger a class-loading cycle.
    weaverBundle_ = FrameworkUtil.getBundle(ServiceLoaderWeaver.class);
  }

  @Override
  public void weave(WovenClass wovenClass)
  {
    if (!shouldWeave(wovenClass))
      {
        return;
      }
    byte[] originalBytes = wovenClass.getBytes();
    ServiceLoaderWeaver.WeavingResult result =
        ServiceLoaderWeaver.weave(originalBytes);

    if (result.getStatus() != ServiceLoaderWeaver.WeavingResult.Status.WOVEN)
      {
        MediatorActivator.logger_.debug( "ServiceLoader weaving did not transform " +
            wovenClass.getClassName() + ": " + result.getStatus());
        return;
      }
    byte[] wovenBytes = result.getBytes();

    printClassBytecode(wovenClass);

    // Add the package visibility before publishing the transformed bytes.
    List<String> dynamicImports = wovenClass.getDynamicImports();

    if (!dynamicImports.contains(MEDIATOR_PACKAGE))
      {
        dynamicImports.add(MEDIATOR_PACKAGE);
      }

    wovenClass.setBytes(wovenBytes);

    printClassBytecode(wovenClass);
  }

  /**
   * Determines whether the mediator should transform this class.
   *
   * <ul>
   *   <li>Statically processed wirings are excluded, with positive header
   *       checks cached for subsequent classes using the same wiring.</li>
   *   <li>Infrastructure bundles are excluded.</li>
   *   <li>bundles whose metadata the mediator cannot read are excluded.</li>
   *   <li>A bundle that selects another mediator is excluded.</li>
   *   <li>A consumer selected by this mediator is always transformed.</li>
   *   <li>A metadata-free consumer is transformed only when framework-level
   *       ServiceLoader support is enabled.</li>
   * </ul>
   *
   * @param wovenClass the class being considered by the weaving hook.
   * @return {@code true} when the mediator should transform the class.
   */
  private boolean shouldWeave(WovenClass wovenClass)
  {
    BundleWiring wiring = wovenClass.getBundleWiring();
    Bundle consumerBundle = wiring.getBundle();

    if (consumerBundle == null ||
        consumerBundle.getBundleId() == 0 ||
        consumerBundle.equals(activator_.getMediatorBundle()) ||
        consumerBundle.equals(weaverBundle_))
      {
        return false;
      }

    if (WeaverConstants.isInfrastructureBundle(consumerBundle.getSymbolicName()))
      {
        return false;
      }

    if (isStaticallyProcessed(wiring, consumerBundle))
      {
        return false;
      }

    if (!MediatorSecurity.canReadMetadata(activator_.getMediatorBundle(),
                                          consumerBundle))
      {
        return false;
      }

    boolean isMetadataConsumer = activator_.isMetadataConsumer(consumerBundle);

    //  Don't process the class if it's bundle defines processor
    //  extender but is not wired to this mediator.
    if (HeaderProcessor.hasProcessorExtenderRequirement(consumerBundle) &&
        !isMetadataConsumer)
      {
        return false;
      }

    // if frameworkServiceLoader is disabled and the consumerBundle doesn't
    // have processor extender.
    if (!activator_.isFrameworkServiceLoaderEnabled() &&
        !isMetadataConsumer)
      {
        return false;
      }
    return true;
  }

  /**
   * Checks the static processing header, caching only positive results by wiring.
   *
   * <p>Only exact statuses accepted by {@link StaticWeavingStatus} count as
   * processed. Invalid values fail class loading and are not cached. Concurrent
   * cache misses may read headers and add the same wiring more than once;
   * the concurrent set deduplicates entries. Cache hits skip header reads.</p>
   *
   * @param wiring wiring used to load the class.
   * @param bundle bundle whose headers are checked on a cache miss.
   * @return {@code true} if the wiring is cached or its status is valid;
   *         {@code false} if the header is absent.
   * @throws WeavingException if the header is present with an invalid value.
   */
  private boolean isStaticallyProcessed(BundleWiring wiring, Bundle bundle)
  {
    if (staticallyProcessedWirings_.contains(wiring))
      {
        return true;
      }
    String value = bundle.getHeaders().get(WeaverConstants.STATIC_WEAVING_HEADER);
    if (value == null)
      {
        return false;
      }
    if (!StaticWeavingStatus.isValid(value))
      {
        throw new WeavingException("Invalid " +
                                   WeaverConstants.STATIC_WEAVING_HEADER +
                                   " value '" + value + "' in bundle " +
                                   bundle.getSymbolicName() +
                                   " [" + bundle.getBundleId() +
                                   "].");
      }
    staticallyProcessedWirings_.add(wiring);
    return true;
  }

  /**
   * Renders the current class bytes for internal debugging.
   */
  private void printClassBytecode(WovenClass wovenClass)
  {
    byte[] bytes = wovenClass.getBytes();

    Textifier textifier = new Textifier();

    TraceClassVisitor tracer =
        new TraceClassVisitor(null, textifier, null);

    ClassReader reader = new ClassReader(bytes);
    reader.accept(tracer, 0);

    StringWriter output = new StringWriter();
    PrintWriter writer = new PrintWriter(output);

    textifier.print(writer);
    writer.flush();

    MediatorActivator.printDebug("[WEAVING_HOOK] Woven class bytecode for " +
                                 " -------\n" + output + "-------------");
  }

}
