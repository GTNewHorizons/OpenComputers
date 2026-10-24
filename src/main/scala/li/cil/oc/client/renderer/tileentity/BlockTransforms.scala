package li.cil.oc.client.renderer.tileentity

import net.minecraftforge.common.util.ForgeDirection
import org.joml.Matrix4f
import org.joml.Matrix4fc
import org.lwjgl.opengl.GL11

object BlockTransforms {
  val Inflated: Matrix4fc = new Matrix4f().translate(0.5f, 0.5f, 0.5f).scale(1.0025f, -1.0025f, 1.0025f).translate(-0.5f, -0.5f, -0.5f)

  val Centered: Matrix4fc = new Matrix4f().translate(0.5f, 0.5f, 0.5f)

  def yawDegrees(yaw: ForgeDirection): Int = yaw match {
    case ForgeDirection.WEST => -90
    case ForgeDirection.NORTH => 180
    case ForgeDirection.EAST => 90
    case _ => 0
  }

  def rotateYaw(yaw: ForgeDirection) {
    val degrees = yawDegrees(yaw)
    if (degrees != 0) GL11.glRotatef(degrees, 0, 1, 0)
  }

  def rotatePitch(pitch: ForgeDirection) {
    pitch match {
      case ForgeDirection.DOWN => GL11.glRotatef(90, 1, 0, 0)
      case ForgeDirection.UP => GL11.glRotatef(-90, 1, 0, 0)
      case _ =>
    }
  }
}

final class FrontTransforms(zFront: Float) {
  private val byYaw = {
    val table = new Array[Matrix4fc](ForgeDirection.values.length)
    for (yaw <- ForgeDirection.values) {
      table(yaw.ordinal) = new Matrix4f().translate(0.5f, 0.5f, 0.5f).rotateY(math.toRadians(BlockTransforms.yawDegrees(yaw)).toFloat).
        translate(-0.5f, 0.5f, zFront).scale(1, -1, 1)
    }
    table
  }

  def apply(yaw: ForgeDirection): Matrix4fc = byYaw(yaw.ordinal)
}
