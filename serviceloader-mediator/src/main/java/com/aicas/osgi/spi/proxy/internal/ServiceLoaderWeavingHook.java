/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.proxy.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;

import com.aicas.osgi.spi.weaver.ServiceLoaderWeaver;

public class ServiceLoaderWeavingHook implements WeavingHook
{
  private final MediatorActivator activator_;
  private final Bundle weaverBundle_;
  private static final String MEDIATOR_PACKAGE =
      com.aicas.osgi.spi.proxy.ServiceLoader.class.getPackage().getName();

  private static final List<String>
  INFRASTRUCTURE_BUNDLE_PREFIXES = List.of("org.objectweb.asm",
                                           "biz.aQute.tester",
                                           "junit-platform-",
                                           "junit-jupiter-",
                                           "org.opentest4j",
                                           "assertj-core",
                                           "net.bytebuddy");

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
    Bundle consumerBundle = wovenClass.getBundleWiring().getBundle();

    if (isInfrastructureBundle(consumerBundle))
      {
        return;
      }

    //  Don't process the class if it's bundle defines processor
    //  extender but is not wired to this mediator.
    if (HeaderProcessor.hasProcessorExtenderRequirement(consumerBundle) &&
        !activator_.requiresProcessing(consumerBundle))
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
   * Returns whether this is a framework bundle which must not be transformed.
   *
   * <p>The hook must not weave its own mediator bundle: doing so requires
   * loading mediator classes while their definitions are being transformed.
   * The weaver and system bundles are similarly framework infrastructure,
   * rather than Service Loader consumers.</p>
   */
  private boolean isInfrastructureBundle(Bundle bundle)
  {
    if (bundle == null ||
        bundle.getBundleId() == 0 ||
        bundle.equals(activator_.getMediatorBundle()) ||
        bundle.equals(weaverBundle_))
      {
        return true;
      }

    String symbolicName = bundle.getSymbolicName();
    return symbolicName != null &&
           INFRASTRUCTURE_BUNDLE_PREFIXES.stream().anyMatch(symbolicName::startsWith);
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
