package li.cil.oc.common.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;

public class ClassTransformer implements IClassTransformer {

  private static final Logger log = LogManager.getLogger("OpenComputers");
  private final LaunchClassLoader loader =
    (LaunchClassLoader) ClassTransformer.class.getClassLoader();

  private final ClassConstantPoolParser simpleComponentParser =
    new ClassConstantPoolParser("li/cil/oc/api/network/SimpleComponent");

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
        name.startsWith("li.cil.oc.integration.")) {
        return basicClass;
      }

      if (name.startsWith("li.cil.oc.")) {
        ClassNode classNode = ASMHelpers.newClassNode(basicClass);
        TransformerStripMissingClasses.transform(loader, name, classNode);
        TransformerInjectInterfaces.transform(loader, name, classNode);
        return ASMHelpers.writeClass(loader, classNode, ClassWriter.COMPUTE_MAXS);
      }

      boolean hasSimpleComponent = simpleComponentParser.find(basicClass);
      if (hasSimpleComponent) {
        try {
          byte[] transformedClass = TransformerInjectEnvironmentImplementation.transform(loader, basicClass);
          log.info("Successfully injected component logic into class {}.", name);
          return transformedClass;
        } catch (Throwable e) {
          log.warn("Failed injecting component logic into class {}.", name, e);
          hadSimpleComponentErrors = true;
          return basicClass;
        }
      }
    } catch (Throwable t) {
      log.warn("Something went wrong!", t);
      hadErrors = true;
    }

    return basicClass;
  }
}
