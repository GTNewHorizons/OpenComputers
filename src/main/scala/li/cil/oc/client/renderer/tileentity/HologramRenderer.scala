package li.cil.oc.client.renderer.tileentity

import java.util

import li.cil.oc.Settings
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.ActiveRenderInfo
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.ForgeDirection
import org.joml.Matrix4f
import org.joml.Vector3f

import scala.util.Random

object HologramRenderer extends TileEntitySpecialRenderer {
  private val random = new Random()

  private val model = new Matrix4f()
  private val inverse = new Matrix4f()
  private val camera = new Vector3f()

  // Blocks; the near plane clips front faces before the camera reaches the box, which would expose culled back faces.
  private final val NearPlanePad = 0.1f

  private var faces = new Array[Int](1 << 14)

  override def renderTileEntityAt(te: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    RenderState.checkError(this, ".renderTileEntityAt: entering (aka: wasntme)")

    val hologram = te.asInstanceOf[Hologram]
    if (!hologram.hasPower) return

    val alpha = HologramFade.alpha(x * x + y * y + z * z, hologram.getFadeStartDistanceSquared, hologram.getMaxRenderDistanceSquared)
    if (alpha <= 0) return

    HologramMesh.active.draw(hologram, alpha, x, y, z, f)

    RenderState.checkError(this, ".renderTileEntityAt: leaving")
  }

  def applyTransform(hologram: Hologram, x: Double, y: Double, z: Double, f: Float): Boolean = {
    val m = model.identity()
    m.translate((x + 0.5).toFloat, (y + 0.5).toFloat, (z + 0.5).toFloat)

    val yaw = BlockTransforms.yawDegrees(hologram.yaw)
    if (yaw != 0) m.rotateY(math.toRadians(yaw).toFloat)
    hologram.pitch match {
      case ForgeDirection.DOWN => m.rotateX(math.toRadians(90).toFloat)
      case ForgeDirection.UP => m.rotateX(math.toRadians(-90).toFloat)
      case _ =>
    }

    rotate(m, hologram.rotationAngle, hologram.rotationX, hologram.rotationY, hologram.rotationZ)
    rotate(m, hologram.rotationSpeed * (hologram.getWorldObj.getTotalWorldTime % (360 * 20 - 1) + f) / 20f, hologram.rotationSpeedX, hologram.rotationSpeedY, hologram.rotationSpeedZ)

    m.scale(1.001f) // Avoid z-fighting with other blocks.
    m.translate(
      ((hologram.translation.xCoord * hologram.width / 16 - 1.5) * hologram.scale).toFloat,
      (hologram.translation.yCoord * hologram.height / 16 * hologram.scale).toFloat,
      ((hologram.translation.zCoord * hologram.width / 16 - 1.5) * hologram.scale).toFloat)

    // Do a bit of flickering, because that's what holograms do!
    if (Settings.get.hologramFlickerFrequency > 0 && random.nextDouble() < Settings.get.hologramFlickerFrequency) {
      m.scale((1 + random.nextGaussian() * 0.01).toFloat, (1 + random.nextGaussian() * 0.001).toFloat, (1 + random.nextGaussian() * 0.01).toFloat)
      m.translate((random.nextGaussian() * 0.01).toFloat, (random.nextGaussian() * 0.01).toFloat, (random.nextGaussian() * 0.01).toFloat)
    }

    // After the below scaling, hologram is drawn inside a [0..48]x[0..32]x[0..48] box
    m.scale((hologram.scale / 16f).toFloat)

    FixedFunction.multMatrix(m)

    m.invertAffine(inverse)
    camera.set(ActiveRenderInfo.objectX, ActiveRenderInfo.objectY, ActiveRenderInfo.objectZ)
    inverse.transformPosition(camera)
    val pad = NearPlanePad * 16 / (hologram.scale * 1.001f).toFloat
    camera.x >= -pad && camera.x <= hologram.width + pad && camera.y >= -pad && camera.y <= hologram.height + pad &&
      camera.z >= -pad && camera.z <= hologram.width + pad
  }

  private def rotate(m: Matrix4f, degrees: Float, ax: Float, ay: Float, az: Float) {
    val length = math.sqrt(ax * ax + ay * ay + az * az).toFloat
    if (degrees != 0 && length > 0) m.rotate(math.toRadians(degrees).toFloat, ax / length, ay / length, az / length)
  }

  private def isSolid(hologram: Hologram, hx: Int, hy: Int, hz: Int) =
    hx >= 0 && hy >= 0 && hz >= 0 && hx < hologram.width && hy < hologram.height && hz < hologram.width && hologram.getColor(hx, hy, hz) != 0

  private def isFaceVisible(hologram: Hologram, hx: Int, hy: Int, hz: Int, face: Int) =
    !isSolid(hologram, hx + HologramFaces.faceNormals(face * 3), hy + HologramFaces.faceNormals(face * 3 + 1), hz + HologramFaces.faceNormals(face * 3 + 2))

  def collectFaces(hologram: Hologram): Int = {
    val width = hologram.width
    val height = hologram.height
    var count = 0
    var hx = 0
    while (hx < width) {
      var hz = 0
      while (hz < width) {
        var hy = 0
        while (hy < height) {
          val value = hologram.getColor(hx, hy, hz)
          if (value != 0) {
            var face = 0
            while (face < HologramFaces.FaceCount) {
              if (isFaceVisible(hologram, hx, hy, hz, face)) {
                if (count == faces.length) faces = util.Arrays.copyOf(faces, count * 2)
                faces(count) = HologramFaces.pack(hx, hy, hz, face, value)
                count += 1
              }
              face += 1
            }
          }
          hy += 1
        }
        hz += 1
      }
      hx += 1
    }
    count
  }

  def emitFaces(t: Tessellator, hologram: Hologram, count: Int) {
    var i = 0
    while (i < count) {
      val packed = faces(i)
      val hx = HologramFaces.x(packed)
      val hy = HologramFaces.y(packed)
      val hz = HologramFaces.z(packed)
      val face = HologramFaces.face(packed)
      val color = hologram.colors(HologramFaces.value(packed) - 1)
      t.setColorOpaque(color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF)
      t.setNormal(HologramFaces.faceNormals(face * 3), HologramFaces.faceNormals(face * 3 + 1), HologramFaces.faceNormals(face * 3 + 2))
      var v = face * HologramFaces.FaceStride
      val end = v + HologramFaces.FaceStride
      while (v < end) {
        t.addVertexWithUV(hx + HologramFaces.faceVertices(v), hy + HologramFaces.faceVertices(v + 1), hz + HologramFaces.faceVertices(v + 2), HologramFaces.faceVertices(v + 3), HologramFaces.faceVertices(v + 4))
        v += 5
      }
      i += 1
    }
  }
}
