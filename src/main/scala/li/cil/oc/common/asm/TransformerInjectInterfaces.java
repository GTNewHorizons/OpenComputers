package li.cil.oc.common.asm;

import li.cil.oc.OpenComputers;
import li.cil.oc.integration.Mods;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class TransformerInjectInterfaces {

  // Inject available interfaces where requested.
  public static byte[] transform(LaunchClassLoader loader, String name, byte[] classBytes) {
    ClassNode classNode = ASMHelpers.newClassNode(classBytes);

    if (classNode.visibleAnnotations != null) {
      for (AnnotationNode annotation : classNode.visibleAnnotations) {
        if (annotation.desc.equals("Lli/cil/oc/common/asm/Injectable$Interface;")) {
          injectInterface(loader, name, classNode, annotation);
          break;
        }

        if (annotation.desc.equals("Lli/cil/oc/common/asm/Injectable$InterfaceList;")) {
          Object value = getAnnotationField(annotation, "value");
          if (value instanceof Iterable<?>) {
            for (Object item : (Iterable<?>) value) {
              if (item instanceof AnnotationNode) {
                injectInterface(loader, name, classNode, (AnnotationNode) item);
              }
            }
          }
          break;
        }
      }
    }

    return ASMHelpers.writeClass(loader, classNode, ClassWriter.COMPUTE_MAXS);
  }

  private static void injectInterface(LaunchClassLoader loader, String ownerName, ClassNode classNode,
    AnnotationNode annotation) {

    Object interfaceObj = getAnnotationField(annotation, "value");
    Object modIdObj = getAnnotationField(annotation, "modid");

    if (!(interfaceObj instanceof String) || !(modIdObj instanceof String)) {
      return;
    }

    String interfaceName = (String) interfaceObj;
    String modId = (String) modIdObj;

    for (scala.collection.Iterator<Mods.ModBase> it = Mods.All().iterator(); it.hasNext(); ) {
      Mods.ModBase mod = it.next();
      if (!mod.id().equals(modId)) {
        continue;
      }

      if (!mod.isAvailable()) {
        OpenComputers.log().info("Skipping interface {} from missing mod {}.", interfaceName, modId);
        mod.disablePower();
        return;
      }

      String interfaceNameInternal = interfaceName.replace('.', '/');
      ClassNode node = ASMHelpers.classNodeFor(loader, interfaceNameInternal);
      if (node == null) {
        OpenComputers.log().warn("Interface {} not found, skipping injection.", interfaceName);
        return;
      }

      boolean isMissing = false;
      for (MethodNode nodeMethod : node.methods) {
        boolean found = false;
        for (MethodNode classNodeMethod : classNode.methods) {
          if (nodeMethod.name.equals(classNodeMethod.name) && nodeMethod.desc.equals(classNodeMethod.desc)) {
            found = true;
            break;
          }
        }
        if (found) continue;

        if (!isMissing) {
          OpenComputers.log().warn("Missing implementations for interface {}, skipping injection.", interfaceName);
          ClassTransformer.hadErrors = true;
          isMissing = true;
        }
        OpenComputers.log().warn("Missing implementation of " + nodeMethod.name + nodeMethod.desc);
      }

      if (!isMissing) {
        OpenComputers.log().info("Injecting interface {} into {}.", interfaceName, ownerName);
        classNode.interfaces.add(interfaceNameInternal);
      }
    }
  }

  private static Object getAnnotationField(AnnotationNode annotation, String field) {
    for (int i = 0; i < annotation.values.size(); i += 2) {
      String key = (String) annotation.values.get(i);
      if (key.equals(field)) {
        return annotation.values.get(i + 1);
      }
    }
    return null;
  }
}
