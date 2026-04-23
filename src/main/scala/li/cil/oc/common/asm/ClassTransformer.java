package li.cil.oc.common.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
          ASMHelpers.dumpClass(transformedName, basicClass, patched, TransformerEntityLiving.class);
          return patched;
        }

        hadErrors = true;
        return basicClass;
      }

      if (transformedName.equals("net.minecraft.client.renderer.entity.RenderLiving")) {
        byte[] patched = TransformerRenderLiving.transform(loader, basicClass);
        if (patched != null) {
          ASMHelpers.dumpClass(transformedName, basicClass, patched, TransformerRenderLiving.class);
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
        name.startsWith("org.apache.")) {
        return basicClass;
      }

      if (name.startsWith("li.cil.oc.")) {
        if (name.startsWith("li.cil.oc.common")) {
          byte[] patched = TransformerInjectInterfaces.transform(loader, name, basicClass);
          if (patched != null) {
            ASMHelpers.dumpClass(transformedName, basicClass, patched, TransformerInjectInterfaces.class);
            return patched;
          }
        }

        return basicClass;
      }

      boolean hasSimpleComponent = simpleComponentParser.find(basicClass);
      if (hasSimpleComponent) {
        try {
          byte[] patched = TransformerInjectEnvironmentImplementation.transform(loader, basicClass);
          if (patched != null) {
            ASMHelpers.dumpClass(transformedName, basicClass, patched, TransformerInjectEnvironmentImplementation.class);
            log.info("Successfully injected component logic into class {}.", name);
            return patched;
          }
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
