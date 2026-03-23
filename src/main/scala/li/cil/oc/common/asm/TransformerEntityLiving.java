package li.cil.oc.common.asm;

import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.commons.lang3.ArrayUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import javax.annotation.Nullable;

public final class TransformerEntityLiving {

  // Inject some code into the EntityLiving classes recreateLeash method to allow
  // proper loading of leashes tied to entities using the leash upgrade. This is
  // necessary because entities only save the entity they are leashed to if that
  // entity is an EntityLivingBase - which drones, for example, are not, for good
  // reason. We work around this by re-leashing them in the load method of the
  // leash upgrade. The leashed entity would then still unleash itself and, more
  // problematically drop a leash item. To avoid this, we extend the
  //    if (this.isLeashed && this.field_110170_bx != null)
  // check to read
  //    if (this.isLeashed && this.field_110170_bx != null && this.leashedToEntity == null)
  // which should not interfere with any existing logic, but avoid leashing
  // restored manually in the load phase to not be broken again.
  @Nullable
  public static byte[] transform(LaunchClassLoader loader, byte[] classBytes) {
    ClassNode classNode = ASMHelpers.newClassNode(classBytes);

    return ASMHelpers.insertInto(
      loader,
      classNode,
      ObfNames.METHOD_RECREATE_LEASH,
      ObfNames.METHOD_RECREATE_LEASH_DESC,
      instructions -> {

        FieldNode leashedToEntityField = null;
        for (FieldNode field : classNode.fields) {
          if (ArrayUtils.contains(ObfNames.FIELD_LEASHED_TO_ENTITY, field.name)) {
            leashedToEntityField = field;
            break;
          }
        }
        if (leashedToEntityField == null) return false;

        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
          if (node instanceof VarInsnNode &&
            node.getNext() instanceof FieldInsnNode &&
            node.getNext().getNext() instanceof JumpInsnNode) {

            VarInsnNode varNode = (VarInsnNode) node;
            FieldInsnNode fieldNode =  (FieldInsnNode) node.getNext();
            JumpInsnNode jumpNode =  (JumpInsnNode) node.getNext().getNext();

            if (varNode.getOpcode() == Opcodes.ALOAD && varNode.var == 0 &&
              fieldNode.getOpcode() == Opcodes.GETFIELD &&
              ArrayUtils.contains(ObfNames.FIELD_LEASH_NBT_TAG, fieldNode.name) &&
              jumpNode.getOpcode() == Opcodes.IFNULL) {

              InsnList toInject = new InsnList();
              toInject.add(new VarInsnNode(Opcodes.ALOAD, 0));
              toInject.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, leashedToEntityField.name, leashedToEntityField.desc));
              toInject.add(new JumpInsnNode(Opcodes.IFNONNULL, jumpNode.label));
              instructions.insert(jumpNode, toInject);
              return true;
            }
          }
        }

        return false;
      });
  }
}
