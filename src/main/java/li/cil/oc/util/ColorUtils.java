package li.cil.oc.util;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

  private static final ColorResource.Factory color = new ColorResource.Factory("opencomputers");

  public static final ColorResource
  // spotless:off
      neiDocTitle  = color.rgb("neiDocTitle", "0x000000"),
      neiDocText   = color.rgb("neiDocText",  "0x333333");
  // spotless:on
}