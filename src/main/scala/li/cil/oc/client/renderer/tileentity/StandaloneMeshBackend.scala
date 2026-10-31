package li.cil.oc.client.renderer.tileentity

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.client.renderer.UnitQuad
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.client.renderer.shading.MeshDraw
import li.cil.oc.client.renderer.shading.Programs
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20

object StandaloneMeshBackend extends MeshBackend {
  MinecraftForge.EVENT_BUS.register(this)

  var atlasGeneration = 0

  @SubscribeEvent
  def onTextureStitch(e: TextureStitchEvent.Post) {
    atlasGeneration += 1
  }

  private lazy val upIndicator =
    CapturedMesh.build(DefaultVertexFormat.POSITION_TEXTURE, Textures.blockScreenUpIndicator)(UnitQuad.emitFrontQuad)

  private val lightMeshes = new Array[CapturedMesh](RobotLight.Steps)

  override def overlayRenderer(overlay: Overlay): TileEntitySpecialRenderer = new StandaloneOverlayRenderer(overlay)

  override def drawRobotPiece(top: Boolean) {
    val mesh = RobotRenderer.pieceMesh(top)
    if (Programs.fixedFunction(inWorld = true)) mesh.draw()
    else {
      val program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
      GL11.glPushAttrib(RenderState.DrawAttribBits)
      MeshDraw.useLit(1f, vertexColor = false)
      mesh.draw()
      GL11.glPopAttrib()
      GL20.glUseProgram(program)
    }
  }

  override def drawRobotLight(step: Int, red: Float, green: Float, blue: Float) {
    var mesh = lightMeshes(step)
    if (mesh == null) {
      mesh = CapturedMesh.build(DefaultVertexFormat.POSITION_TEXTURE, Textures.blockRobot)(RobotRenderer.emitLight(_, step))
      lightMeshes(step) = mesh
    }
    MeshDraw.drawUnlit(mesh, MeshDraw.Identity, Blend.Opaque, red, green, blue, 1, inWorld = true)
  }

  override def drawUpIndicator() {
    GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT)
    GL11.glDepthMask(false)
    MeshDraw.drawUnlit(upIndicator, MeshDraw.Identity, Blend.Alpha, 1, 1, 1, 1, inWorld = true)
    GL11.glPopAttrib()
  }
}
