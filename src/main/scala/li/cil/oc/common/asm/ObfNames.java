package li.cil.oc.common.asm;

public final class ObfNames {
  private ObfNames() {}

  public static final String[] CLASS_ENTITY_HANGING = { "net/minecraft/entity/EntityHanging", "ss" };
  public static final String[] CLASS_ENTITY_LIVING = { "net/minecraft/entity/EntityLiving", "sw" };
  public static final String[] CLASS_RENDER_LIVING = { "net/minecraft/client/renderer/entity/RenderLiving", "bok" };
  public static final String[] CLASS_TILE_ENTITY = { "net/minecraft/tileentity/TileEntity", "aor" };

  public static final String[] FIELD_LEASH_NBT_TAG = { "leashNBTTag", "field_110170_bx", "bx" };
  public static final String[] FIELD_LEASHED_TO_ENTITY = { "leashedToEntity", "field_110168_bw", "bw" };

  public static final String[] METHOD_RECREATE_LEASH = { "recreateLeash", "func_110165_bF", "bP" };
  public static final String[] METHOD_RECREATE_LEASH_DESC = { "()V" };

  public static final String[] METHOD_RENDER_HANGING = { "func_110827_b", "b" };
  public static final String[] METHOD_RENDER_HANGING_DESC = { "(Lsw;DDDFF)V", "(Lnet/minecraft/entity/EntityLiving;DDDFF)V" };

  public static final String[] METHOD_VALIDATE = { "validate", "func_145829_t" };
  public static final String[] METHOD_INVALIDATE = { "invalidate", "func_145843_s" };
  public static final String[] METHOD_ON_CHUNK_UNLOAD = { "onChunkUnload", "func_76623_d" };
  public static final String[] METHOD_READ_FROM_NBT = { "readFromNBT", "func_145839_a" };
  public static final String[] METHOD_WRITE_TO_NBT = { "writeToNBT", "func_145841_b" };
}
