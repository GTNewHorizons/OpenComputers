package li.cil.oc.common.asm;

import com.gtnewhorizon.gtnhlib.asm.ASMUtil;
import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Predicate;

public final class ASMHelpers {

  private static final Logger log = LogManager.getLogger("OpenComputers");
  private static final boolean dumpASMClass = Boolean.getBoolean("opencomputers.dumpClass");

  @Nullable
  public static byte[] insertInto(LaunchClassLoader loader, ClassNode classNode, String[] methodNames, String[] methodDescs, Predicate<InsnList> inserter) {
    MethodNode methodNode = null;
    for (MethodNode method : classNode.methods) {
      if (ArrayUtils.contains(methodNames, method.name) && ArrayUtils.contains(methodDescs, method.desc)) {
        methodNode = method;
        break;
      }
    }

    if (methodNode == null) {
      log.warn("Failed patching {}.{}, method not found.", classNode.name, methodNames[0]);
      return null;
    }

    if (!inserter.test(methodNode.instructions)) {
      log.warn("Failed patching {}.{}, injection point not found.", classNode.name, methodNames[0]);
      return null;
    }

    log.info("Successfully patched {}.{}.", classNode.name, methodNames[0]);
    return writeClass(loader, classNode, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
  }

  @Nonnull
  public static byte[] writeClass(LaunchClassLoader loader, ClassNode classNode, int flags) {
    ClassWriter writer = new ClassWriter(flags) {
      // Implementation without class loads, avoids https://github.com/MinecraftForge/FML/issues/655
      @Override
      protected String getCommonSuperClass(final String type1, final String type2) {
        ClassNode node1 = classNodeFor(loader, type1);
        ClassNode node2 = classNodeFor(loader, type2);

        if (isAssignable(loader, node1, node2)) return node1.name;
        if (isAssignable(loader, node2, node1)) return node2.name;
        if (isInterface(node1) || isInterface(node2)) return "java/lang/Object";

        ClassNode parent = node1;
        while (parent != null && parent.superName != null && !isAssignable(loader, parent, node2)) {
          parent = classNodeFor(loader, parent.superName);
        }

        if (parent == null) return "java/lang/Object";
        return parent.name;
      }
    };

    classNode.accept(writer);
    return writer.toByteArray();
  }

  @Nullable
  public static byte[] classBytesFor(LaunchClassLoader loader, String internalName) {
    try {
      // internalName is slash-form, e.g. "net/minecraft/tileentity/TileEntity"
      String namePlain = internalName.replace('/', '.');

      final byte[] bytes = loader.getClassBytes(namePlain);
      if (bytes != null) {
        return bytes;
      }

      String nameObf = FMLDeobfuscatingRemapper.INSTANCE.unmap(internalName).replace('/', '.');
      if (nameObf.equals(namePlain)) {
        return null;
      }

      return loader.getClassBytes(nameObf);
    } catch (IOException ignored) {
      return null;
    }
  }

  @Nullable
  public static ClassNode classNodeFor(LaunchClassLoader loader, String internalName) {
    final byte[] bytes = classBytesFor(loader, internalName);
    if (bytes == null) {
      return null;
    }

    return newClassNode(bytes);
  }

  @Nonnull
  public static ClassNode newClassNode(byte[] data) {
    ClassNode node = new ClassNode();
    new ClassReader(data).accept(node, 0);
    return node;
  }

  public static void dumpClass(String className, byte[] originalBytes, byte[] transformedBytes, Object transformer) {
    if (dumpASMClass) {
      ASMUtil.saveAsRawClassFile(originalBytes, className + "_PRE", transformer);
      ASMUtil.saveAsRawClassFile(transformedBytes, className + "_POST", transformer);
    }
  }

  public static MethodNode copyMethodNode(MethodNode methodNode) {
    MethodNode newMethodNode = new MethodNode(
      methodNode.access, methodNode.name, methodNode.desc, methodNode.signature,
      methodNode.exceptions == null ? null : methodNode.exceptions.toArray(new String[0])
    );
    methodNode.accept(newMethodNode); // clones instructions/labels/etc
    return newMethodNode;
  }

  public static boolean isAssignable(LaunchClassLoader loader, ClassNode parent, ClassNode child) {
    if (parent == null || child == null) return false;
    if (isFinal(parent)) return false;

    if (parent.name.equals("java/lang/Object")) return true;
    if (parent.name.equals(child.name)) return true;
    if (parent.name.equals(child.superName)) return true;
    if (child.interfaces.contains(parent.name)) return true;

    if (child.superName != null) {
      return isAssignable(loader, parent, classNodeFor(loader, child.superName));
    }

    return false;
  }

  public static boolean isFinal(ClassNode node) {
    return (node.access & Opcodes.ACC_FINAL) != 0;
  }

  public static boolean isInterface(ClassNode node) {
    return node != null && (node.access & Opcodes.ACC_INTERFACE) != 0;
  }

  public static MethodNode findMethod(ClassNode classNode, String name, String desc) {
    for (MethodNode method : classNode.methods) {
      if (name.equals(method.name) && desc.equals(method.desc)) return method;
    }
    return null;
  }
}
