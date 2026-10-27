package li.cil.oc.client.renderer.tileentity

import net.minecraft.client.renderer.Tessellator
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import org.joml.Matrix4fc

trait OverlaySink {
  def bucket(texture: ResourceLocation): Tessellator
}

// animated: modelview, color or lightmap change on most visible frames, so the mesh is streamed instead of retained.
abstract class Overlay(val states: Int, val blendMode: Int, val animated: Boolean = false) {
  def state(te: TileEntity): Int = 0

  def alpha(te: TileEntity): Float = 1f

  def transform(te: TileEntity): Matrix4fc

  def emit(sink: OverlaySink, state: Int): Unit
}
