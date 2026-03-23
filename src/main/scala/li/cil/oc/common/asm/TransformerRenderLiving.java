package li.cil.oc.common.asm;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import javax.annotation.Nullable;

public final class TransformerRenderLiving {

  // Little change to the renderer used to render leashes to center it on drones.
  // This injects the code
  //   if (entity instanceof Drone) {
  //     d5 = 0.0;
  //     d6 = 0.0;
  //     d7 = -0.75;
  //   }
  // before the `instanceof EntityHanging` check in func_110827_b.
  @Nullable
  public static byte[] transform(LaunchClassLoader loader, byte[] classBytes) {
    ClassNode classNode = ASMHelpers.newClassNode(classBytes);

    return ASMHelpers.insertInto(
      loader,
      classNode,
      ObfNames.METHOD_RENDER_HANGING,
      ObfNames.METHOD_RENDER_HANGING_DESC,
      instructions -> {

        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
          if (node instanceof VarInsnNode &&
            node.getNext() instanceof TypeInsnNode &&
            node.getNext().getNext() instanceof JumpInsnNode) {

            VarInsnNode varNode = (VarInsnNode) node;
            TypeInsnNode typeNode =  (TypeInsnNode) node.getNext();
            JumpInsnNode jumpNode =  (JumpInsnNode) node.getNext().getNext();

            if (varNode.getOpcode() == Opcodes.ALOAD && varNode.var == 10
              && typeNode.getOpcode() == Opcodes.INSTANCEOF
              && ArrayUtils.contains(ObfNames.CLASS_ENTITY_HANGING, typeNode.desc)
              && jumpNode.getOpcode() == Opcodes.IFEQ) {

              InsnList toInject = new InsnList();

              toInject.add(new VarInsnNode(Opcodes.ALOAD, 10));
              toInject.add(new TypeInsnNode(Opcodes.INSTANCEOF, "li/cil/oc/common/entity/Drone"));

              LabelNode skip = new LabelNode();
              toInject.add(new JumpInsnNode(Opcodes.IFEQ, skip));

              toInject.add(new LdcInsnNode(0.0));
              toInject.add(new VarInsnNode(Opcodes.DSTORE, 16));
              toInject.add(new LdcInsnNode(0.0));
              toInject.add(new VarInsnNode(Opcodes.DSTORE, 18));
              toInject.add(new LdcInsnNode(-0.75));
              toInject.add(new VarInsnNode(Opcodes.DSTORE, 20));

              toInject.add(skip);

              instructions.insertBefore(varNode, toInject);
              return true;
            }
          }
        }
        return false;
      });
  }
}
