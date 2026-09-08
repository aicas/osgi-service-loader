/*------------------------------------------------------------------------*
 * Copyright 2026, aicas GmbH; all rights reserved.
 * This header, including copyright notice, may not be altered or removed.
 *------------------------------------------------------------------------*/

package com.aicas.osgi.spi.weaver;

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import java.io.Serial;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Transactionally rewrites supported {@code java.util.ServiceLoader}
 * references for the OSGi Service Loader proxy.
 *
 * <p>Calls to {@code load(Class)} and {@code load(Class, ClassLoader)} are
 * redirected to private static synthetic {@code serviceLoaderBridge$load}
 * methods generated on the woven class. Each bridge preserves the original
 * call signature, then forwards to the corresponding proxy overload with the
 * woven class literal as its caller argument. Directly supported instance
 * methods continue to be remapped to the proxy type.</p>
 *
 * <p>Method handles targeting either static {@code load} overload in LDC
 * constants, {@code invokedynamic} bootstrap arguments, and constant-dynamic
 * values are redirected to the same bridges, preserving method-reference
 * signatures. Other supported ServiceLoader handles are remapped to the proxy
 * type. A bridge name receives a numeric suffix when the woven class already
 * declares that name and descriptor.</p>
 *
 * <p>If an unsupported ServiceLoader invocation is encountered, processing
 * stops immediately and the original class bytes are returned unchanged.</p>
 *
 * <p>If the class does not contain any supported ServiceLoader invocation, no
 * transformed byte array is generated and the original bytes are returned.</p>
 */
public final class ServiceLoaderWeaver
{
  private static final java.util.logging.Logger LOGGER =
      java.util.logging.Logger.getLogger(ServiceLoaderWeaver.class.getName());

  static final String JAVA_SERVICE_LOADER = "java/util/ServiceLoader";

  /** JVM type descriptor for {@link java.util.ServiceLoader}. */
  static final String JAVA_SERVICE_LOADER_DESCRIPTOR =
      "L" + JAVA_SERVICE_LOADER + ";";

  static final String OSGI_SERVICE_LOADER = "com/aicas/osgi/spi/proxy/ServiceLoader";

  /** JVM type descriptor for the proxy ServiceLoader. */
  static final String OSGI_SERVICE_LOADER_DESCRIPTOR = "L" + OSGI_SERVICE_LOADER + ";";

  /** Original JDK descriptor for {@code ServiceLoader.load(Class)}. */
  static final String JAVA_LOAD_DESCRIPTOR =
      "(Ljava/lang/Class;)" + JAVA_SERVICE_LOADER_DESCRIPTOR;

  /** Original JDK descriptor for {@code ServiceLoader.load(Class, ClassLoader)}. */
  static final String JAVA_LOAD_WITH_LOADER_DESCRIPTOR =
      "(Ljava/lang/Class;Ljava/lang/ClassLoader;)" + JAVA_SERVICE_LOADER_DESCRIPTOR;

  /** Descriptor of the generated one-argument bridge method. */
  static final String OSGI_LOAD_DESCRIPTOR =
      "(Ljava/lang/Class;)" + OSGI_SERVICE_LOADER_DESCRIPTOR;

  /** Descriptor of the generated two-argument bridge method. */
  static final String OSGI_LOAD_WITH_LOADER_DESCRIPTOR =
      "(Ljava/lang/Class;Ljava/lang/ClassLoader;)" + OSGI_SERVICE_LOADER_DESCRIPTOR;

  /** Descriptor of the proxy call made by the one-argument bridge. */
  static final String OSGI_BRIDGE_LOAD_DESCRIPTOR =
      "(Ljava/lang/Class;Ljava/lang/Class;)" + OSGI_SERVICE_LOADER_DESCRIPTOR;

  /** Descriptor of the proxy call made by the two-argument bridge. */
  static final String OSGI_BRIDGE_LOAD_WITH_LOADER_DESCRIPTOR =
      "(Ljava/lang/Class;Ljava/lang/ClassLoader;Ljava/lang/Class;)" +
          OSGI_SERVICE_LOADER_DESCRIPTOR;

  static final String BRIDGE_LOAD_NAME = "serviceLoaderBridge$load";

  private static final ServiceLoaderTypeRemapper SERVICE_LOADER_TYPE_REMAPPER =
      new ServiceLoaderTypeRemapper();

  /** The supported load forms and the descriptors used at each rewrite stage. */
  private enum LoadBridge
  {
    LOAD(JAVA_LOAD_DESCRIPTOR,
         OSGI_LOAD_DESCRIPTOR,
         OSGI_BRIDGE_LOAD_DESCRIPTOR,
         1),
    LOAD_WITH_LOADER(JAVA_LOAD_WITH_LOADER_DESCRIPTOR,
                     OSGI_LOAD_WITH_LOADER_DESCRIPTOR,
                     OSGI_BRIDGE_LOAD_WITH_LOADER_DESCRIPTOR,
                     2);

    private final String javaDescriptor;
    private final String bridgeDescriptor;
    private final String proxyDescriptor;
    private final int argumentCount;

    LoadBridge(String javaDescriptor,
               String bridgeDescriptor,
               String proxyDescriptor,
               int argumentCount)
    {
      this.javaDescriptor = javaDescriptor;
      this.bridgeDescriptor = bridgeDescriptor;
      this.proxyDescriptor = proxyDescriptor;
      this.argumentCount = argumentCount;
    }

    private static LoadBridge forJavaDescriptor(String descriptor)
    {
      for (LoadBridge bridge : values())
        {
          if (bridge.javaDescriptor.equals(descriptor))
            {
              return bridge;
            }
        }
      return null;
    }
  }

  /**
   * Supported methods are identified using the original JDK owner,
   * invocation opcode, method name, and descriptor.
   *
   * <p>To add support for another ServiceLoader method, add its exact method
   * key to this set and provide the corresponding method in
   * {@code com.aicas.osgi.spi.proxy.ServiceLoader}.</p>
   */
  private static final Set<MethodKey> SUPPORTED_METHODS;
  static
    {
      Set<MethodKey> methods = new HashSet<MethodKey>();

      methods.add(new MethodKey(INVOKESTATIC,
                                "load",
                                JAVA_LOAD_DESCRIPTOR));

      methods.add(new MethodKey(INVOKESTATIC,
                                "load",
                                JAVA_LOAD_WITH_LOADER_DESCRIPTOR));

      methods.add(new MethodKey(INVOKEVIRTUAL,
                                "iterator",
                                "()Ljava/util/Iterator;"));

      methods.add(new MethodKey(INVOKEVIRTUAL,
                                "reload",
                                "()V"));

      methods.add(new MethodKey(INVOKEVIRTUAL,
                                "findFirst",
                                "()Ljava/util/Optional;"));

      methods.add(new MethodKey(INVOKEVIRTUAL, "toString",
                                "()Ljava/lang/String;"));

      SUPPORTED_METHODS = Collections.unmodifiableSet(methods);
    }


  private ServiceLoaderWeaver()
  {
  }

  /**
   * Attempts to weave one class and generate any required load bridges.
   *
   * <p>The supplied byte array is never modified. When weaving cannot be
   * completed, the result contains the original byte array and reports the
   * reason through its {@linkplain WeavingResult#getStatus() status}.</p>
   *
   * <p>Successful weaving remaps ServiceLoader type references, redirects the
   * two static {@code load} overloads and their method handles to bridge
   * targets on the woven class, and remaps the remaining supported calls and
   * handles to the proxy type.</p>
   *
   * @param originalBytes original class-file bytes
   *
   * @return the weaving result
   *
   * @throws NullPointerException if {@code originalBytes} is {@code null}
   */
  public static WeavingResult weave(byte[] originalBytes)
  {
    Objects.requireNonNull(originalBytes, "originalBytes");

    try
      {
        ClassReader reader = new ClassReader(originalBytes);
        /*
         * Preserve and remap the class's existing stack-map frames. Computing
         * frames would make ASM's ClassWriter load remapped types through the
         * weaver bundle's class loader. That loader cannot necessarily see the
         * mediator's proxy ServiceLoader and would fail with
         * TypeNotPresentException from getCommonSuperClass().
         *
         * This transformation preserves the stack shape of existing methods
         * and only adds straight-line bridge methods, so computing maximum
         * stack/local sizes is sufficient.
         */
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

        ServiceLoaderClassVisitor visitor =
          new ServiceLoaderClassVisitor(writer);

        /*
         * Validation and transformation happen during this single traversal.
         */
        reader.accept(visitor, 0);

        /*
         * Do not call writer.toByteArray() when no supported invocation was
         * found.
         */
        if (!visitor.hasSupportedInvocation())
          {
            return WeavingResult.noSupportedInvocation(originalBytes);
          }

        /*
         * This point is reached only when the complete class was processed
         * without encountering an unsupported invocation.
         */
        return WeavingResult.woven(writer.toByteArray());
      }
    catch (UnsupportedInvocationException exception)
      {
        /*
         * Expected rejection: the class invokes an unsupported ServiceLoader
         * method.
         */
        return WeavingResult.unsupportedInvocation(originalBytes);
      }
    catch (RuntimeException exception)
      {
        /*
         * The weaver is also used directly by unit tests and can be called
         * before the OSGi activator has initialized its logger.
         */
        LOGGER.log(Level.FINE, "Failed to weave class", exception);
        /*
         * Malformed bytecode, ASM failure, or another transformation error.
         * The original class remains usable.
         */
        return WeavingResult.transformationError(originalBytes, exception);
      }
  }

  /**
   * Remaps JDK ServiceLoader type references represented in the class file.
   */
  private static final class ServiceLoaderTypeRemapper
    extends Remapper
  {
    @Override
    public String map(String internalName)
    {
      if (JAVA_SERVICE_LOADER.equals(internalName))
        {
          return OSGI_SERVICE_LOADER;
        }

      return internalName;
    }
  }

  /**
   * Performs class-wide type remapping, rewrites supported load call sites,
   * and emits any required bridge methods when the class visit completes.
   */
  private static final class ServiceLoaderClassVisitor
    extends ClassRemapper
  {
    private boolean supportedInvocationFound;
    private final Set<MethodSignature> declaredMethods = new HashSet<>();
    private final Map<LoadBridge, String> bridgeNames = new EnumMap<>(LoadBridge.class);
    private String wovenClassName;

    private ServiceLoaderClassVisitor(ClassVisitor delegate)
    {
      super(Opcodes.ASM9, delegate, SERVICE_LOADER_TYPE_REMAPPER);
    }

    @Override
    public void visit(int version,
                      int access,
                      String name,
                      String signature,
                      String superName,
                      String[] interfaces)
    {
      wovenClassName = name;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String descriptor,
                                     String signature,
                                     String[] exceptions)
    {
      declaredMethods.add(new MethodSignature(name,
                                              SERVICE_LOADER_TYPE_REMAPPER.mapMethodDesc(descriptor)));
      /*
       * ClassRemapper returns a MethodRemapper here. Our visitor wraps that
       * MethodRemapper so that validation sees the original instruction before
       * the delegate remaps it.
       */
      MethodVisitor remappingVisitor = super.visitMethod(access,
                                                         name,
                                                         descriptor,
                                                         signature,
                                                         exceptions);

      if (remappingVisitor == null)
        {
          return null;
        }

      return new ServiceLoaderMethodVisitor(remappingVisitor, this);
    }

    private void markSupportedInvocation()
    {
      supportedInvocationFound = true;
    }

    private boolean hasSupportedInvocation()
    {
      return supportedInvocationFound;
    }

    /**
     * Returns the generated bridge for one load signature, allocating a name
     * that does not collide with a remapped declared method.
     */
    private String getLoadBridgeName(LoadBridge bridge)
    {
      String bridgeName = bridgeNames.get(bridge);
      if (bridgeName == null)
        {
          bridgeName = allocateBridgeName(bridge.bridgeDescriptor);
          bridgeNames.put(bridge, bridgeName);
        }
      return bridgeName;
    }

    private String allocateBridgeName(String descriptor)
    {
      String name = BRIDGE_LOAD_NAME;
      int suffix = 1;
      while (declaredMethods.contains(new MethodSignature(name, descriptor)))
        {
          name = BRIDGE_LOAD_NAME + "$" + suffix++;
        }
      declaredMethods.add(new MethodSignature(name, descriptor));
      return name;
    }

    private String getWovenClassName()
    {
      return wovenClassName;
    }

    @Override
    public void visitEnd()
    {
      for (LoadBridge bridge : LoadBridge.values())
        {
          String bridgeName = bridgeNames.get(bridge);
          if (bridgeName != null)
            {
              addLoadBridge(bridgeName, bridge);
            }
        }
      super.visitEnd();
    }

    /**
     * Emits a private static synthetic bridge that appends this woven class's
     * class literal before calling the proxy load overload.
     */
    private void addLoadBridge(String name, LoadBridge bridge)
    {
      MethodVisitor bridgeMethod = super.visitMethod(
          Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
          name, bridge.bridgeDescriptor, null, null);
      bridgeMethod.visitCode();
      bridgeMethod.visitVarInsn(Opcodes.ALOAD, 0);
      if (bridge.argumentCount == 2)
        {
          bridgeMethod.visitVarInsn(Opcodes.ALOAD, 1);
        }
      bridgeMethod.visitLdcInsn(Type.getObjectType(wovenClassName));
      bridgeMethod.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          OSGI_SERVICE_LOADER,
          "load",
          bridge.proxyDescriptor,
          false);
      bridgeMethod.visitInsn(Opcodes.ARETURN);
      bridgeMethod.visitMaxs(0, 0);
      bridgeMethod.visitEnd();
    }
  }

  /**
   * Validates ServiceLoader invocations, redirects supported load forms to
   * generated bridges, and forwards all remaining supported forms to ASM's
   * type remapper.
   */
  private static final class ServiceLoaderMethodVisitor extends MethodVisitor
  {
    private final ServiceLoaderClassVisitor classVisitor;

    private ServiceLoaderMethodVisitor(
                                       MethodVisitor delegate,
                                       ServiceLoaderClassVisitor classVisitor)
    {
      super(Opcodes.ASM9, delegate);
      this.classVisitor = classVisitor;
    }

    @Override
    public void visitMethodInsn(int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface)
    {
      LoadBridge bridge = loadBridgeForInvocation(opcode, owner, name, descriptor);
      if (bridge != null)
        {
          validateInvocation(opcode, owner, name, descriptor);
          super.visitMethodInsn(
              INVOKESTATIC,
              classVisitor.getWovenClassName(),
              classVisitor.getLoadBridgeName(bridge),
              bridge.bridgeDescriptor,
              false);
          return;
        }
      validateInvocation(opcode,
                         owner,
                         name,
                         descriptor);

      /*
       * The delegate is ASM's MethodRemapper. It remaps both the invocation
       * owner and ServiceLoader occurrences in the descriptor.
       */
      super.visitMethodInsn(opcode,
                            owner,
                            name,
                            descriptor,
                            isInterface);
    }

    @Override
    public void visitInvokeDynamicInsn(String name,
                                       String descriptor,
                                       Handle bootstrapMethodHandle,
                                       Object... bootstrapMethodArguments)
    {
      /* Rewrite ServiceLoader method handles used by lambda/method references. */
      super.visitInvokeDynamicInsn(name,
                                   descriptor,
                                   rewriteHandle(bootstrapMethodHandle),
                                   rewriteConstants(bootstrapMethodArguments));
    }

    @Override
    public void visitLdcInsn(Object value)
    {
      /* A direct method handle or ConstantDynamic can also target load. */
      super.visitLdcInsn(rewriteConstant(value));
    }

    private Object[] rewriteConstants(Object[] values)
    {
      Object[] rewritten = new Object[values.length];
      for (int index = 0; index < values.length; index++)
        {
          rewritten[index] = rewriteConstant(values[index]);
        }
      return rewritten;
    }

    private Object rewriteConstant(Object value)
    {
      if (value instanceof Handle)
        {
          return rewriteHandle((Handle) value);
        }
      if (value instanceof ConstantDynamic)
        {
          ConstantDynamic constant = (ConstantDynamic) value;
          Object[] arguments = new Object[constant.getBootstrapMethodArgumentCount()];
          for (int index = 0; index < arguments.length; index++)
            {
              arguments[index] = rewriteConstant(constant.getBootstrapMethodArgument(index));
            }
          return new ConstantDynamic(constant.getName(),
                                     constant.getDescriptor(),
                                     rewriteHandle(constant.getBootstrapMethod()),
                                     arguments);
        }
      return value;
    }

    /**
     * Redirects a supported static load method handle to its bridge while
     * retaining the handle's original argument shape for method references.
     */
    private Handle rewriteHandle(Handle handle)
    {
      validateHandle(handle);
      LoadBridge bridge = loadBridgeForInvocation(
          handle.getTag() == H_INVOKESTATIC ? INVOKESTATIC : -1,
          handle.getOwner(), handle.getName(), handle.getDesc());
      if (bridge != null)
        {
          return new Handle(H_INVOKESTATIC,
                            classVisitor.getWovenClassName(),
                            classVisitor.getLoadBridgeName(bridge),
                            bridge.bridgeDescriptor,
                            false);
        }
      return handle;
    }

    private static LoadBridge loadBridgeForInvocation(int opcode,
                                                       String owner,
                                                       String name,
                                                       String descriptor)
    {
      return opcode == INVOKESTATIC && JAVA_SERVICE_LOADER.equals(owner) &&
             "load".equals(name) ? LoadBridge.forJavaDescriptor(descriptor) : null;
    }

    private void validateHandle(Handle handle)
    {
      if (!JAVA_SERVICE_LOADER.equals(handle.getOwner()))
        {
          return;
        }

      int opcode;

      switch (handle.getTag())
        {
        case H_INVOKESTATIC:
          opcode = INVOKESTATIC;
          break;

        case H_INVOKEVIRTUAL:
          opcode = INVOKEVIRTUAL;
          break;

        default:
          /*
           * INVOKEINTERFACE, INVOKESPECIAL, constructors, and field handles
           * are not supported for java.util.ServiceLoader.
           */
          throw UnsupportedInvocationException.INSTANCE;
        }

      validateInvocation(opcode,
                         handle.getOwner(),
                         handle.getName(),
                         handle.getDesc());
    }

    private void validateInvocation(int opcode,
                                    String owner,
                                    String name,
                                    String descriptor)
    {
      if (!JAVA_SERVICE_LOADER.equals(owner))
        {
          return;
        }

      /*
       * Only INVOKESTATIC and INVOKEVIRTUAL are valid supported forms.
       */
      if (opcode != INVOKESTATIC && opcode != INVOKEVIRTUAL)
        {
          throw UnsupportedInvocationException.INSTANCE;
        }

      MethodKey invocation = new MethodKey(opcode, name, descriptor);

      if (!SUPPORTED_METHODS.contains(invocation))
        {
          /*
           * Abort the ClassReader traversal immediately. Any partially written
           * data in ClassWriter is discarded.
           */
          throw UnsupportedInvocationException.INSTANCE;
        }

      classVisitor.markSupportedInvocation();
    }
  }

  /** Identifies a declared or generated method by its JVM name and descriptor. */
  private static final class MethodSignature
  {
    private final String name;
    private final String descriptor;

    private MethodSignature(String name, String descriptor)
    {
      this.name = name;
      this.descriptor = descriptor;
    }

    @Override
    public boolean equals(Object object)
    {
      if (!(object instanceof MethodSignature))
        {
          return false;
        }
      MethodSignature other = (MethodSignature) object;
      return name.equals(other.name) && descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode()
    {
      return 31 * name.hashCode() + descriptor.hashCode();
    }
  }

  /**
   * Identifies one supported ServiceLoader method invocation.
   */
  private static final class MethodKey
  {
    private final int opcode;
    private final String name;
    private final String descriptor;

    private MethodKey(int opcode,
                      String name,
                      String descriptor)
    {
      this.opcode = opcode;
      this.name = Objects.requireNonNull(name, "name");
      this.descriptor =
        Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public boolean equals(Object object)
    {
      if (this == object)
        {
          return true;
        }

      if (!(object instanceof MethodKey))
        {
          return false;
        }

      MethodKey other = (MethodKey) object;

      return opcode == other.opcode && name.equals(other.name) &&
             descriptor.equals(other.descriptor);
    }

    @Override
    public int hashCode()
    {
      int result = Integer.hashCode(opcode);
      result = 31 * result + name.hashCode();
      result = 31 * result + descriptor.hashCode();
      return result;
    }
  }

  /**
   * Stops the current ASM traversal when an unsupported invocation is found.
   *
   * <p>No stack trace is created because this exception represents an expected
   * validation result rather than an implementation error.</p>
   */
  private static final class UnsupportedInvocationException
    extends RuntimeException
  {
    @Serial
    private static final long serialVersionUID = 1L;

    private static final UnsupportedInvocationException INSTANCE =
      new UnsupportedInvocationException();

    private UnsupportedInvocationException()
    {
      super(null, null, false, false);
    }
  }

  /**
   * Result returned by {@link ServiceLoaderWeaver#weave(byte[])}.
   */
  public static final class WeavingResult
  {
    /** The possible outcomes of one weaving attempt. */
    public enum Status
    {
      /** Woven class bytes were produced. */
      WOVEN,

      /** The class contains no ServiceLoader invocation that the weaver supports. */
      NO_SUPPORTED_INVOCATION,

      /** The class contains a ServiceLoader invocation that the weaver does not support. */
      UNSUPPORTED_INVOCATION,

      /** Parsing or transformation failed; the original bytes are retained. */
      TRANSFORMATION_ERROR
    }

    private final Status status;
    private final byte[] bytes;

    private WeavingResult(Status status,
                          byte[] bytes)
    {
      this.status = Objects.requireNonNull(status, "status");
      this.bytes = Objects.requireNonNull(bytes, "bytes");
    }

    private static WeavingResult woven(byte[] wovenBytes)
    {
      return new WeavingResult(Status.WOVEN,
                               wovenBytes);
    }

    private static WeavingResult noSupportedInvocation(byte[] originalBytes)
    {
      return new WeavingResult(Status.NO_SUPPORTED_INVOCATION,
                               originalBytes);
    }

    private static WeavingResult unsupportedInvocation(byte[] originalBytes)
    {
      return new WeavingResult(Status.UNSUPPORTED_INVOCATION,
                               originalBytes);
    }

    private static WeavingResult transformationError(byte[] originalBytes,
                                                     RuntimeException exception)
    {
      return new WeavingResult(Status.TRANSFORMATION_ERROR,
                               originalBytes);
    }

    /**
     * Returns the outcome of the weaving attempt.
     *
     * @return the weaving status
     */
    public Status getStatus()
    {
      return status;
    }

    /**
     * Returns whether the complete class was successfully woven.
     *
     * @return {@code true} if woven bytes were produced; otherwise
     *         {@code false}
     *
     * @deprecated use {@link #getStatus()} and compare it with
     *             {@link Status#WOVEN}
     */
    @Deprecated
    public boolean isSuccess()
    {
      return status == Status.WOVEN;
    }

    /**
     * Returns the woven bytes when {@link #getStatus()} is {@link Status#WOVEN},
     * or the original bytes otherwise.
     *
     * @return class-file bytes
     */
    public byte[] getBytes()
    {
      return bytes;
    }
  }
}
