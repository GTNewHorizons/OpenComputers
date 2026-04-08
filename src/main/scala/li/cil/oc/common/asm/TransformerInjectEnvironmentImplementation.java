package li.cil.oc.common.asm;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import li.cil.oc.common.asm.template.SimpleComponentImpl;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Predicate;

public final class TransformerInjectEnvironmentImplementation {

  private static final Logger log = LogManager.getLogger("OpenComputers");

  @Nullable
  private static ClassNode template = null;

  @Nonnull
  public static byte[] transform(LaunchClassLoader loader, byte[] classBytes) throws Exception {
    ClassNode classNode = ASMHelpers.newClassNode(classBytes);
    log.trace("Injecting methods from Environment interface into {}.", classNode.name);

    if (classNode.visibleAnnotations != null) {
      for (AnnotationNode annotation : classNode.visibleAnnotations) {
        if (annotation != null && annotation.desc.equals("Lli/cil/oc/api/network/SimpleComponent$SkipInjection;")) {
          log.trace("Detected @SimpleComponent.SkipInjection annotation, skipping the class");
          return classBytes;
        }
      }
    }

    if (!isTileEntity(loader, classNode.name, classNode.superName)) {
      throw new Exception("Found SimpleComponent on something that isn't a tile entity, ignoring.");
    }

    if (template == null) {
      template = ASMHelpers.classNodeFor(loader, "li/cil/oc/common/asm/template/SimpleEnvironment");
      if (template == null) {
        throw new Exception("Could not find SimpleComponent template!");
      }
    }

    injectMethodIfMissing(classNode, template, "node", "()Lli/cil/oc/api/network/Node;", true);
    injectMethodIfMissing(classNode, template, "onConnect", "(Lli/cil/oc/api/network/Node;)V", false);
    injectMethodIfMissing(classNode, template, "onDisconnect", "(Lli/cil/oc/api/network/Node;)V", false);
    injectMethodIfMissing(classNode, template, "onMessage", "(Lli/cil/oc/api/network/Message;)V", false);

    log.trace("Injecting / wrapping overrides for required tile entity methods.");

    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_VALIDATE[0], ObfNames.METHOD_VALIDATE[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_INVALIDATE[0], ObfNames.METHOD_INVALIDATE[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_ON_CHUNK_UNLOAD[0], ObfNames.METHOD_ON_CHUNK_UNLOAD[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_READ_FROM_NBT[0], ObfNames.METHOD_READ_FROM_NBT[1], "(Lnet/minecraft/nbt/NBTTagCompound;)V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_WRITE_TO_NBT[0], ObfNames.METHOD_WRITE_TO_NBT[1], "(Lnet/minecraft/nbt/NBTTagCompound;)V");

    log.trace("Injecting interface.");
    classNode.interfaces.add("li/cil/oc/common/asm/template/SimpleComponentImpl");

    return ASMHelpers.writeClass(loader, classNode, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
  }

  private static boolean isTileEntity(LaunchClassLoader loader, String className, String superName) {
    if (ArrayUtils.contains(ObfNames.CLASS_TILE_ENTITY, className)) return true;
    if (superName == null || superName.equals("java/lang/Object")) return false;

    final byte[] bytes = ASMHelpers.classBytesFor(loader, superName);
    if (bytes == null) return false;

    ClassReader classReader = new ClassReader(bytes);
    return isTileEntity(loader, superName, classReader.getSuperName());
  }

  private static void injectMethodIfMissing(ClassNode classNode, ClassNode template, String methodName, String desc,
    boolean required) throws Exception {

    if (ASMHelpers.findMethod(classNode, methodName, desc) != null) {
      if (required) {
        throw new Exception("Could not inject method '" + methodName + desc + "' because it was already present!");
      }
      return;
    }

    MethodNode methodNode = ASMHelpers.findMethod(template, methodName, desc);
    if (methodNode == null) {
      throw new AssertionError("Template missing method " + methodName + desc);
    }

    classNode.methods.add(ASMHelpers.copyMethodNode(methodNode));
  }

  private static void replaceTileMethod(LaunchClassLoader loader, ClassNode classNode, ClassNode template,
   String methodNamePlain, String methodNameSrg, String desc) throws Exception {

    final FMLDeobfuscatingRemapper mapper = FMLDeobfuscatingRemapper.INSTANCE;

    Predicate<MethodNode> filter = method -> {
      if (!methodNamePlain.equals(method.name)) {
        final String methodNameDeObf = mapper.mapMethodName(ObfNames.CLASS_TILE_ENTITY[1], method.name, method.desc);
        if (!methodNameSrg.equals(methodNameDeObf)) return false;
      }

      final String descDeObf = mapper.mapMethodDesc(method.desc);
      return desc.equals(descDeObf);
    };

    for (MethodNode method : classNode.methods) {
      if ((methodNamePlain + SimpleComponentImpl.PostFix).equals(method.name)
        && mapper.mapMethodDesc(method.desc).equals(desc)) {
        throw new Exception("Delegator method name '" + (methodNamePlain + SimpleComponentImpl.PostFix) + "' is already in use.");
      }
    }

    MethodNode original = null;
    for (MethodNode method : classNode.methods) {
      if (filter.test(method)) {
        original = method;
        break;
      }
    }

    if (original != null) {
      log.trace("Found original implementation of '{}', wrapping.", methodNamePlain);
      original.name = methodNamePlain + SimpleComponentImpl.PostFix;
    } else {
      log.trace("No original implementation of '{}', will inject override.", methodNamePlain);

      ensureNonFinalInHierarchy(loader, classNode.superName, filter, methodNamePlain);

      MethodNode delegator = ASMHelpers.findMethod(template, methodNamePlain + SimpleComponentImpl.PostFix, desc);
      if (delegator == null) {
        throw new AssertionError("Couldn't find '" + (methodNamePlain + SimpleComponentImpl.PostFix) + "' in template implementation.");
      }
      classNode.methods.add(ASMHelpers.copyMethodNode(delegator));
    }

    MethodNode override = null;
    for (MethodNode m : template.methods) {
      if (filter.test(m)) {
        override = m;
        break;
      }
    }
    if (override == null) {
      throw new AssertionError("Couldn't find '" + methodNamePlain + "' in template implementation.");
    }
    classNode.methods.add(ASMHelpers.copyMethodNode(override));
  }

  private static void ensureNonFinalInHierarchy(LaunchClassLoader loader, String internalSuperName,
    Predicate<MethodNode> filter, String methodNamePlain) throws Exception {

    if (internalSuperName == null) return;

    ClassNode node = ASMHelpers.classNodeFor(loader, internalSuperName);
    if (node == null) return;

    for (MethodNode method : node.methods) {
      if (filter.test(method) && (method.access & Opcodes.ACC_FINAL) != 0) {
        throw new Exception(
          "Method '" + methodNamePlain + "' is final in superclass " + node.name.replace('/', '.') + "."
        );
      }
    }

    ensureNonFinalInHierarchy(loader, node.superName, filter, methodNamePlain);
  }
}
