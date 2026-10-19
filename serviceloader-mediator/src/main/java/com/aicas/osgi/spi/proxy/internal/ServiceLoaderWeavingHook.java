/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
  /** Fixed metadata classification for a bundle wiring. */
  private enum WiringAnalysis
  {
    SKIP,
    PROCESSOR_REQUIRED,
    METADATA_FREE
  }

  private final MediatorActivator activator_;
  private final Bundle weaverBundle_;

  /**
   * Fixed wiring analyses, held by strong references for this hook's lifetime.
   * Consumer registration and metadata permissions are checked on each load.
   */
  private final ConcurrentMap<BundleWiring, WiringAnalysis> wiringAnalyses_ =
      new ConcurrentHashMap<>();

  ServiceLoaderWeavingHook(MediatorActivator act)
  {
    activator_ = act;
    // Resolve this before registering the hook. Resolving the weaver bundle
    // from weave() can otherwise trigger a class-loading cycle.
    weaverBundle_ = FrameworkUtil.getBundle(ServiceLoaderWeaver.class);
    // Loading the analysis enum from weave() would also re-enter this hook.
    WiringAnalysis.values();
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

    if (!dynamicImports.contains(WeaverConstants.PROXY_PACKAGE))
      {
        dynamicImports.add(WeaverConstants.PROXY_PACKAGE);
      }

    wovenClass.setBytes(wovenBytes);

    printClassBytecode(wovenClass);
  }

  /**
   * Determines whether the mediator should attempt to weave this class.
   *
   * <ul>
   *   <li>Statically processed wirings are excluded.</li>
   *   <li>Infrastructure bundles are excluded.</li>
   *   <li>Bundles whose metadata the mediator cannot currently read are excluded.</li>
   *   <li>A bundle that selects another mediator is excluded.</li>
   *   <li>An otherwise eligible consumer selected by this mediator is processed.</li>
   *   <li>A metadata-free consumer is processed only when framework-level
   *       ServiceLoader support is enabled.</li>
   * </ul>
   *
   * <p>Caches whether the bundle should always be skipped and whether it
   * declares a processor requirement. These properties remain fixed for
   * the same wiring.</p>
   *
   * <p>Does not cache metadata permissions or consumer registration status,
   * because they can change without a new wiring. Invalid static processing
   * headers throw an exception and are not cached.</p>
   *
   * @param wovenClass the class being considered by the weaving hook.
   * @return {@code true} when the mediator should attempt to weave the class.
   * @throws WeavingException if analysis encounters an invalid static processing
   *         header value.
   */
  private boolean shouldWeave(WovenClass wovenClass)
  {
    BundleWiring wiring = wovenClass.getBundleWiring();
    Bundle consumerBundle = wiring.getBundle();
    WiringAnalysis analysis = wiringAnalyses_.get(wiring);
    if (analysis == null &&
        (consumerBundle == null ||
         consumerBundle.getBundleId() == 0 ||
         consumerBundle.equals(activator_.getMediatorBundle()) ||
         consumerBundle.equals(weaverBundle_) ||
         WeaverConstants.isInfrastructureBundle(consumerBundle.getSymbolicName())))
      {
        wiringAnalyses_.put(wiring, WiringAnalysis.SKIP);
        return false;
      }
    if (analysis == WiringAnalysis.SKIP ||
        !MediatorSecurity.canReadMetadata(activator_.getMediatorBundle(),
                                         consumerBundle))
      {
        return false;
      }
    if (analysis == null)
      {
        if (isStaticallyProcessed(consumerBundle))
          {
            analysis = WiringAnalysis.SKIP;
          }
        else
          {
            analysis = HeaderProcessor.hasProcessorExtenderRequirement(consumerBundle) ?
                       WiringAnalysis.PROCESSOR_REQUIRED : WiringAnalysis.METADATA_FREE;
          }
        wiringAnalyses_.put(wiring, analysis);
      }
    if (analysis == WiringAnalysis.SKIP)
      {
        return false;
      }
    return activator_.isMetadataConsumer(consumerBundle) ||
           (analysis == WiringAnalysis.METADATA_FREE &&
            activator_.isFrameworkServiceLoaderEnabled());
  }

  /**
   * Checks the bundle's static processing header.
   *
   * @param bundle bundle whose headers are checked.
   * @return {@code true} if the status is valid; {@code false} if absent.
   * @throws WeavingException if the header is present with an invalid value.
   */
  private boolean isStaticallyProcessed(Bundle bundle)
  {
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
