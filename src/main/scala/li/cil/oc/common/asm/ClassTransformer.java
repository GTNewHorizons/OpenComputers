package li.cil.oc.common.asm;

import li.cil.oc.OpenComputers;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class ClassTransformer implements IClassTransformer {

  private final LaunchClassLoader loader =
    (LaunchClassLoader) ClassTransformer.class.getClassLoader();

  public static boolean hadErrors = false;
  public static boolean hadSimpleComponentErrors = false;

  @Override
  public byte[] transform(String name, String transformedName, byte[] basicClass) {
    if (basicClass == null || name.startsWith("scala.")) {
      return basicClass;
    }

    byte[] transformedClass = basicClass;

    try {
      if (!name.startsWith("net.minecraft.")
        && !name.startsWith("net.minecraftforge.")
        && !name.startsWith("li.cil.oc.common.asm.")
        && !name.startsWith("li.cil.oc.integration.")) {

        if (name.startsWith("li.cil.oc.")) {
          transformedClass = TransformerStripMissingClasses.transform(loader, name, transformedClass);
          transformedClass = TransformerInjectInterfaces.transform(loader, name, transformedClass);
        }

        ClassNode classNode = ASMHelpers.newClassNode(transformedClass);
        boolean hasSimpleComponent = classNode.interfaces.contains("li/cil/oc/api/network/SimpleComponent");
        boolean hasSkipAnnotation = false;

        if (classNode.visibleAnnotations != null) {
          for (AnnotationNode annotation : classNode.visibleAnnotations) {
            if (annotation != null && annotation.desc.equals("Lli/cil/oc/api/network/SimpleComponent$SkipInjection;")) {
              hasSkipAnnotation = true;
              break;
            }
          }
        }

        if (hasSimpleComponent && !hasSkipAnnotation) {
          try {
            transformedClass = TransformerInjectEnvironmentImplementation.transform(loader, classNode);
            OpenComputers.log().info("Successfully injected component logic into class {}.", name);
          } catch (Throwable e) {
            OpenComputers.log().warn("Failed injecting component logic into class {}.", name, e);
            hadSimpleComponentErrors = true;
          }
        }
      }

      String internalName = name.replace('.', '/');

      if (ArrayUtils.contains(ObfNames.CLASS_ENTITY_LIVING, internalName)) {
        byte[] patched = TransformerEntityLiving.transform(loader, transformedClass);
        if (patched != null) {
          transformedClass = patched;
        } else {
          hadErrors = true;
        }
      }

      if (ArrayUtils.contains(ObfNames.CLASS_RENDER_LIVING, internalName)) {
        byte[] patched = TransformerRenderLiving.transform(loader, transformedClass);
        if (patched != null) {
          transformedClass = patched;
        } else {
          hadErrors = true;
        }
      }

      return transformedClass;
    } catch (Throwable t) {
      OpenComputers.log().warn("Something went wrong!", t);
      hadErrors = true;
      return basicClass;
    }
  }
}
