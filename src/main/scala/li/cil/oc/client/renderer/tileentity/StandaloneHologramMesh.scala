package li.cil.oc.client.renderer.tileentity

import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.google.common.cache.RemovalListener
import com.google.common.cache.RemovalNotification
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.shading.FixedFunction
import li.cil.oc.client.renderer.shading.MeshDraw
import li.cil.oc.client.renderer.shading.Programs
import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.util.RenderState
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

object StandaloneHologramMesh extends HologramMesh with RemovalListener[Hologram, CapturedMesh] {
  private val cache = CacheBuilder.newBuilder().
    expireAfterAccess(5, TimeUnit.SECONDS).
    removalListener(this).
    build[Hologram, CapturedMesh]()

  override def init(): Unit = FMLCommonHandler.instance.bus.register(this)

  override def draw(hologram: Hologram, alpha: Float, x: Double, y: Double, z: Double, partialTicks: Float) {
    var mesh = cache.getIfPresent(hologram)
    if (mesh == null) {
      mesh = new CapturedMesh
      cache.put(hologram, mesh)
      hologram.needsRendering = true
    }
    if (hologram.needsRendering) {
      hologram.needsRendering = false
      mesh.reset()
      val count = HologramRenderer.collectFaces(hologram)
      try HologramRenderer.emitFaces(mesh.bucket(DefaultVertexFormat.POSITION_TEXTURE_COLOR_NORMAL, Textures.blockHologram), hologram, count)
      finally mesh.closeBucket()
    }

    val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    GL11.glPushMatrix()
    val inside = HologramRenderer.applyTransform(hologram, x, y, z, partialTicks)

    GL11.glEnable(GL11.GL_BLEND)
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)

    // Seen from inside, the back faces are the only ones in view.
    if (inside) GL11.glDisable(GL11.GL_CULL_FACE)
    else GL11.glEnable(GL11.GL_CULL_FACE)

    if (Programs.fixedFunction(inWorld = true) || !MeshDraw.useLit(alpha, vertexColor = true)) {
      GL11.glPushAttrib(RenderState.DrawAttribBits)
      GL11.glEnable(GL11.GL_NORMALIZE)
      FixedFunction.constantAlphaBlend(alpha)
      drawPasses(mesh)
      GL11.glPopAttrib()
    }
    else drawPasses(mesh)

    GL11.glPopMatrix()
    GL11.glPopAttrib()
    GL20.glUseProgram(program)
  }

  private def drawPasses(mesh: CapturedMesh) {
    GL11.glColorMask(false, false, false, false)
    GL11.glDepthMask(true)
    mesh.draw()
    GL11.glColorMask(true, true, true, true)
    GL11.glDepthFunc(GL11.GL_EQUAL)
    mesh.draw()
  }

  override def onRemoval(e: RemovalNotification[Hologram, CapturedMesh]): Unit = e.getValue.delete()

  @SubscribeEvent
  def onTick(e: ClientTickEvent) = cache.cleanUp()
}
