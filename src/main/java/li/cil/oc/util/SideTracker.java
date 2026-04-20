package li.cil.oc.util;

import cpw.mods.fml.common.FMLCommonHandler;

public final class SideTracker {

  public static boolean isServer() {
    return FMLCommonHandler.instance().getEffectiveSide().isServer();
  }

  public static boolean isClient() {
    return !isServer();
  }
}
