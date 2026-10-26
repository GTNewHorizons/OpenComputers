package li.cil.oc.client.renderer.tileentity

import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.Tessellator
import net.minecraft.util.IIcon
import net.minecraftforge.common.util.ForgeDirection

object CubeFaces {
  final val Walls = ForgeDirection.NORTH.flag | ForgeDirection.SOUTH.flag | ForgeDirection.WEST.flag | ForgeDirection.EAST.flag

  def openSides(te: tileentity.traits.OpenSides): Int = {
    val sides = ForgeDirection.VALID_DIRECTIONS
    var mask = 0
    var i = 0
    while (i < sides.length) {
      if (te.isSideOpen(sides(i))) mask |= sides(i).flag
      i += 1
    }
    mask
  }

  private final val Inflation = 0.00125

  def emitTop(t: Tessellator, icon: IIcon): Unit = emitTop(t, icon, 0, 0, 0, 1, 1)

  def emitSides(t: Tessellator, icon: IIcon, mask: Int): Unit = emitSides(t, icon, mask, 0, 0, 0, 1, 1, 1)

  def emitInflatedTop(t: Tessellator, icon: IIcon, x: Int, y: Int, z: Int): Unit =
    emitTop(t, icon, x - Inflation, y + 1 + Inflation, z - Inflation, x + 1 + Inflation, z + 1 + Inflation)

  def emitInflatedSides(t: Tessellator, icon: IIcon, mask: Int, x: Int, y: Int, z: Int): Unit =
    emitSides(t, icon, mask, x - Inflation, y + 1 + Inflation, z - Inflation, x + 1 + Inflation, y - Inflation, z + 1 + Inflation)

  def emitTop(t: Tessellator, icon: IIcon, x0: Double, y0: Double, z0: Double, x1: Double, z1: Double) {
    t.addVertexWithUV(x0, y0, z1, icon.getMinU, icon.getMaxV)
    t.addVertexWithUV(x1, y0, z1, icon.getMaxU, icon.getMaxV)
    t.addVertexWithUV(x1, y0, z0, icon.getMaxU, icon.getMinV)
    t.addVertexWithUV(x0, y0, z0, icon.getMinU, icon.getMinV)
  }

  def emitSides(t: Tessellator, icon: IIcon, mask: Int, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) {
    if ((mask & ForgeDirection.DOWN.flag) != 0) {
      t.addVertexWithUV(x0, y1, z0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x1, y1, z0, icon.getMinU, icon.getMinV)
      t.addVertexWithUV(x1, y1, z1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(x0, y1, z1, icon.getMaxU, icon.getMaxV)
    }
    if ((mask & ForgeDirection.UP.flag) != 0) {
      t.addVertexWithUV(x0, y0, z0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(x0, y0, z1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x1, y0, z1, icon.getMinU, icon.getMinV)
      t.addVertexWithUV(x1, y0, z0, icon.getMinU, icon.getMaxV)
    }
    if ((mask & ForgeDirection.NORTH.flag) != 0) {
      t.addVertexWithUV(x1, y1, z0, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(x0, y1, z0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(x0, y0, z0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x1, y0, z0, icon.getMinU, icon.getMinV)
    }
    if ((mask & ForgeDirection.SOUTH.flag) != 0) {
      t.addVertexWithUV(x0, y1, z1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(x1, y1, z1, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(x1, y0, z1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x0, y0, z1, icon.getMinU, icon.getMinV)
    }
    if ((mask & ForgeDirection.WEST.flag) != 0) {
      t.addVertexWithUV(x0, y1, z0, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(x0, y1, z1, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(x0, y0, z1, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x0, y0, z0, icon.getMinU, icon.getMinV)
    }
    if ((mask & ForgeDirection.EAST.flag) != 0) {
      t.addVertexWithUV(x1, y1, z1, icon.getMinU, icon.getMaxV)
      t.addVertexWithUV(x1, y1, z0, icon.getMaxU, icon.getMaxV)
      t.addVertexWithUV(x1, y0, z0, icon.getMaxU, icon.getMinV)
      t.addVertexWithUV(x1, y0, z1, icon.getMinU, icon.getMinV)
    }
  }
}
