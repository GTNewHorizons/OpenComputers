package li.cil.oc.common.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class ClassTransformer implements IClassTransformer {

  private static final Logger log = LogManager.getLogger("OpenComputers");
  private final LaunchClassLoader loader =
    (LaunchClassLoader) ClassTransformer.class.getClassLoader();

  public static boolean hadErrors = false;
  public static boolean hadSimpleComponentErrors = false;

  @Override
  public byte[] transform(String name, String transformedName, byte[] basicClass) {
    if (basicClass == null) {
      return null;
    }

    try {
      if (transformedName.equals("net.minecraft.entity.EntityLiving")) {
        byte[] patched = TransformerEntityLiving.transform(loader, basicClass);
        if (patched != null) {
          return patched;
        }

        hadErrors = true;
        return basicClass;
      }

      if (transformedName.equals("net.minecraft.client.renderer.entity.RenderLiving")) {
        byte[] patched = TransformerRenderLiving.transform(loader, basicClass);
        if (patched != null) {
          return patched;
        }

        hadErrors = true;
        return basicClass;
      }

      if (name.startsWith("scala.") ||
        name.startsWith("net.minecraft.") ||
        name.startsWith("net.minecraftforge.") ||
        name.startsWith("cpw.mods.fml.") ||
        // We're using apache's ArrayUtils here, so we need to avoid circular transforms of this class
        name.startsWith("org.apache.") ||
        name.startsWith("li.cil.oc.common.asm.") ||
        name.startsWith("li.cil.oc.integration.")) {
        return basicClass;
      }

      byte[] transformedClass = basicClass;

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
          log.info("Successfully injected component logic into class {}.", name);
        } catch (Throwable e) {
          log.warn("Failed injecting component logic into class {}.", name, e);
          hadSimpleComponentErrors = true;
        }
      }

      return transformedClass;
    } catch (Throwable t) {
      log.warn("Something went wrong!", t);
      hadErrors = true;
      return basicClass;
    }
  }
}
