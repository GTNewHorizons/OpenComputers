package li.cil.oc.client.renderer

import java.util

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IndexBuffer
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VAOManager
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Tessellator
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

object CapturedMesh {
  def build(format: VertexFormat, texture: ResourceLocation)(emit: Tessellator => Unit): CapturedMesh = {
    val mesh = new CapturedMesh
    try emit(mesh.bucket(format, texture))
    finally mesh.closeBucket()
    mesh
  }

  private final class Slot(val format: VertexFormat) {
    val ebo = new IndexBuffer()

    val vao: IVertexArrayObject = VAOManager.createMutableVAO(format, GL11.GL_TRIANGLES, ebo)

    var texture: ResourceLocation = _
  }
}

final class CapturedMesh {
  private val slots = new util.ArrayList[CapturedMesh.Slot]

  private var used = 0

  private var capturing: DirectTessellator = _

  private var capturingFormat: VertexFormat = _

  private var capturingTexture: ResourceLocation = _

  def bucket(format: VertexFormat, texture: ResourceLocation): Tessellator = {
    closeBucket()
    val t = TessellatorManager.startCapturingDirect(format)
    t.startDrawingQuads()
    t.draw()
    capturing = t
    capturingFormat = format
    capturingTexture = texture
    t
  }

  def closeBucket() {
    if (capturing != null) {
      val t = capturing
      capturing = null
      try if (!t.isEmpty) {
        val slot = nextSlot(capturingFormat)
        t.allocateToVBO(slot.vao, slot.ebo)
        slot.texture = capturingTexture
      }
      finally TessellatorManager.stopCapturingDirect()
      capturingFormat = null
      capturingTexture = null
    }
  }

  def reset() {
    closeBucket()
    used = 0
  }

  def delete() {
    closeBucket()
    var i = 0
    while (i < slots.size) {
      slots.get(i).vao.delete()
      i += 1
    }
    slots.clear()
    used = 0
  }

  def draw() {
    val textureManager = Minecraft.getMinecraft.getTextureManager
    var i = 0
    while (i < used) {
      val slot = slots.get(i)
      if (slot.texture == null) GL11.glDisable(GL11.GL_TEXTURE_2D)
      else {
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        textureManager.bindTexture(slot.texture)
      }
      slot.vao.render()
      i += 1
    }
  }

  private def nextSlot(format: VertexFormat): CapturedMesh.Slot = {
    var i = used
    while (i < slots.size && (slots.get(i).format ne format)) i += 1
    if (i == slots.size) slots.add(new CapturedMesh.Slot(format))
    val slot = slots.get(i)
    slots.set(i, slots.get(used))
    slots.set(used, slot)
    used += 1
    slot
  }
}
