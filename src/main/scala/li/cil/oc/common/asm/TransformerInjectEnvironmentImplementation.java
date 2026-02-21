package li.cil.oc.common.asm;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import li.cil.oc.OpenComputers;
import li.cil.oc.common.asm.template.SimpleComponentImpl;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

public final class TransformerInjectEnvironmentImplementation {

  @Nonnull
  public static byte[] transform(LaunchClassLoader loader, ClassNode classNode) throws Exception {
    OpenComputers.log().trace("Injecting methods from Environment interface into {}.", classNode.name);

    if (!isTileEntity(loader, classNode)) {
      throw new Exception("Found SimpleComponent on something that isn't a tile entity, ignoring.");
    }

    ClassNode template = ASMHelpers.classNodeFor(loader, "li/cil/oc/common/asm/template/SimpleEnvironment");
    if (template == null) {
      throw new Exception("Could not find SimpleComponent template!");
    }

    injectMethodIfMissing(classNode, template, "node", "()Lli/cil/oc/api/network/Node;", true);
    injectMethodIfMissing(classNode, template, "onConnect", "(Lli/cil/oc/api/network/Node;)V", false);
    injectMethodIfMissing(classNode, template, "onDisconnect", "(Lli/cil/oc/api/network/Node;)V", false);
    injectMethodIfMissing(classNode, template, "onMessage", "(Lli/cil/oc/api/network/Message;)V", false);

    OpenComputers.log().trace("Injecting / wrapping overrides for required tile entity methods.");

    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_VALIDATE[0], ObfNames.METHOD_VALIDATE[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_INVALIDATE[0], ObfNames.METHOD_INVALIDATE[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_ON_CHUNK_UNLOAD[0], ObfNames.METHOD_ON_CHUNK_UNLOAD[1], "()V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_READ_FROM_NBT[0], ObfNames.METHOD_READ_FROM_NBT[1], "(Lnet/minecraft/nbt/NBTTagCompound;)V");
    replaceTileMethod(loader, classNode, template, ObfNames.METHOD_WRITE_TO_NBT[0], ObfNames.METHOD_WRITE_TO_NBT[1], "(Lnet/minecraft/nbt/NBTTagCompound;)V");

    OpenComputers.log().trace("Injecting interface.");
    classNode.interfaces.add("li/cil/oc/common/asm/template/SimpleComponentImpl");

    return ASMHelpers.writeClass(loader, classNode, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
  }

  private static boolean isTileEntity(LaunchClassLoader loader, ClassNode classNode) {
    if (classNode == null) return false;
    OpenComputers.log().trace("Checking if class {} is a TileEntity...", classNode.name);
    if (ArrayUtils.contains(ObfNames.CLASS_TILE_ENTITY, classNode.name)) return true;
    return classNode.superName != null && isTileEntity(loader, ASMHelpers.classNodeFor(loader, classNode.superName));
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

    classNode.methods.add(methodNode);
  }

  private static void replaceTileMethod(LaunchClassLoader loader, ClassNode classNode, ClassNode template,
   String methodNamePlain, String methodNameSrg, String desc) throws Exception {

    FMLDeobfuscatingRemapper mapper = FMLDeobfuscatingRemapper.INSTANCE;

    Predicate<MethodNode> filter = method -> {
      String descDeObf = mapper.mapMethodDesc(method.desc);
      String methodNameDeObf = mapper.mapMethodName(ObfNames.CLASS_TILE_ENTITY[1], method.name, method.desc);

      boolean samePlain = (method.name + descDeObf).equals(methodNamePlain + desc);
      boolean sameDeObf = (methodNameDeObf + descDeObf).equals(methodNameSrg + desc);

      return samePlain || sameDeObf;
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
      OpenComputers.log().trace("Found original implementation of '{}', wrapping.", methodNamePlain);
      original.name = methodNamePlain + SimpleComponentImpl.PostFix;
    } else {
      OpenComputers.log().trace("No original implementation of '{}', will inject override.", methodNamePlain);

      ensureNonFinalInHierarchy(loader, classNode.superName, filter, methodNamePlain);

      MethodNode delegator = ASMHelpers.findMethod(template, methodNamePlain + SimpleComponentImpl.PostFix, desc);
      if (delegator == null) {
        throw new AssertionError("Couldn't find '" + (methodNamePlain + SimpleComponentImpl.PostFix) + "' in template implementation.");
      }
      classNode.methods.add(delegator);
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
    classNode.methods.add(override);
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
