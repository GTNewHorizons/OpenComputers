package li.cil.oc.client.renderer

import net.minecraft.client.renderer.Tessellator

object DebugBox {
  private val boxFaceOrder = Array(0, 2, 1, 3, 4, 5)

  def drawBox(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double) {
    val t = Tessellator.instance
    t.startDrawingQuads()
    var i = 0
    while (i < boxFaceOrder.length) {
      emitBoxFace(t, minX, minY, minZ, maxX, maxY, maxZ, boxFaceOrder(i))
      i += 1
    }
    t.draw()
  }

  def drawBoxFace(minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, side: Int) {
    val t = Tessellator.instance
    t.startDrawingQuads()
    emitBoxFace(t, minX, minY, minZ, maxX, maxY, maxZ, side)
    t.draw()
  }

  private def emitBoxFace(t: Tessellator, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, side: Int): Unit = side match {
    case 0 =>
      t.addVertex(minX, minY, minZ)
      t.addVertex(minX, minY, maxZ)
      t.addVertex(maxX, minY, maxZ)
      t.addVertex(maxX, minY, minZ)
    case 1 =>
      t.addVertex(maxX, maxY, minZ)
      t.addVertex(maxX, maxY, maxZ)
      t.addVertex(minX, maxY, maxZ)
      t.addVertex(minX, maxY, minZ)
    case 2 =>
      t.addVertex(minX, minY, minZ)
      t.addVertex(maxX, minY, minZ)
      t.addVertex(maxX, maxY, minZ)
      t.addVertex(minX, maxY, minZ)
    case 3 =>
      t.addVertex(maxX, maxY, maxZ)
      t.addVertex(maxX, minY, maxZ)
      t.addVertex(minX, minY, maxZ)
      t.addVertex(minX, maxY, maxZ)
    case 4 =>
      t.addVertex(minX, minY, minZ)
      t.addVertex(minX, maxY, minZ)
      t.addVertex(minX, maxY, maxZ)
      t.addVertex(minX, minY, maxZ)
    case 5 =>
      t.addVertex(maxX, minY, minZ)
      t.addVertex(maxX, minY, maxZ)
      t.addVertex(maxX, maxY, maxZ)
      t.addVertex(maxX, maxY, minZ)
    case _ =>
  }
}
