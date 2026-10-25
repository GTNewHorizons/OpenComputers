package li.cil.oc.client.renderer.tileentity

import com.google.common.base.Strings
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat
import java.util
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.driver.item.UpgradeRenderer
import li.cil.oc.api.driver.item.UpgradeRenderer.MountPointName
import li.cil.oc.api.event.RobotRenderEvent
import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.CapturedMesh
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity
import li.cil.oc.util.RenderState
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderBlocks
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.client.renderer.entity.RendererLivingEntity
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.init.Items
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumChatFormatting
import net.minecraftforge.client.IItemRenderer.ItemRenderType._
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper._
import net.minecraftforge.client.MinecraftForgeClient
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12

object RobotRenderer extends TileEntitySpecialRenderer {
  private val mountPointNames = Array(
    UpgradeRenderer.MountPointName.TopLeft,
    UpgradeRenderer.MountPointName.TopRight,
    UpgradeRenderer.MountPointName.TopBack,
    UpgradeRenderer.MountPointName.BottomLeft,
    UpgradeRenderer.MountPointName.BottomRight,
    UpgradeRenderer.MountPointName.BottomBack,
    UpgradeRenderer.MountPointName.BottomFront
  )

  private val slotIndices = new util.HashMap[String, Integer]()
  for (index <- mountPointNames.indices) {
    slotIndices.put(mountPointNames(index), index)
  }

  private final class RenderSlots {
    val mountPoints = new Array[RobotRenderEvent.MountPoint](mountPointNames.length)
    for (index <- mountPointNames.indices) {
      mountPoints(index) = new RobotRenderEvent.MountPoint(mountPointNames(index))
    }
    val availableSlots = new util.HashSet[String]()
    val mountStacks = new Array[ItemStack](mountPoints.length)
    val mountRenderers = new Array[UpgradeRenderer](mountPoints.length)
    var wildcardStacks = new Array[ItemStack](4)
    var wildcardRenderers = new Array[UpgradeRenderer](4)
    var wildcardCount = 0

    def clear() {
      util.Arrays.fill(mountStacks.asInstanceOf[Array[AnyRef]], null)
      util.Arrays.fill(mountRenderers.asInstanceOf[Array[AnyRef]], null)
      util.Arrays.fill(wildcardStacks.asInstanceOf[Array[AnyRef]], 0, wildcardCount, null)
      util.Arrays.fill(wildcardRenderers.asInstanceOf[Array[AnyRef]], 0, wildcardCount, null)
      wildcardCount = 0
    }
  }

  // Render thread only. Upgrade renderers and event handlers may render another robot, so state is per nesting depth.
  private val slotPool = new util.ArrayList[RenderSlots]()
  private var depth = 0

  private def enter(): RenderSlots = {
    if (depth == slotPool.size) slotPool.add(new RenderSlots)
    val slots = slotPool.get(depth)
    depth += 1
    slots
  }

  private def exit() {
    depth -= 1
  }

  private val gap = 1.0f / 28.0f
  private val gt = 0.5f + gap
  private val gb = 0.5f - gap

  private val size = 0.4f
  private val l = 0.5f - size
  private val h = 0.5f + size

  private def setNormal(t: Tessellator, x: Float, y: Float, z: Float) {
    val il = 1f / math.sqrt(x * x + y * y + z * z).toFloat
    t.setNormal(x * il, y * il, z * il)
  }

  // Degenerate quad: the captured chassis mesh is drawn as GL_QUADS.
  private def fanQuad(t: Tessellator, apexY: Float, apexU: Float,
                      nx: Float, ny: Float, nz: Float,
                      x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
                      x2: Float, y2: Float, z2: Float, u2: Float, v2: Float) {
    setNormal(t, nx, ny, nz)
    t.addVertexWithUV(0.5f, apexY, 0.5f, apexU, 0.25)
    t.addVertexWithUV(x1, y1, z1, u1, v1)
    t.addVertexWithUV(x2, y2, z2, u2, v2)
    t.addVertexWithUV(x2, y2, z2, u2, v2)
  }

  private def emitTopPiece(t: Tessellator) {
    fanQuad(t, 1, 0.25f, 0, 0.2f, 1, l, gt, h, 0, 0.5f, h, gt, h, 0.5f, 0.5f)
    fanQuad(t, 1, 0.25f, 1, 0.2f, 0, h, gt, h, 0.5f, 0.5f, h, gt, l, 0.5f, 0)
    fanQuad(t, 1, 0.25f, 0, 0.2f, -1, h, gt, l, 0.5f, 0, l, gt, l, 0, 0)
    fanQuad(t, 1, 0.25f, -1, 0.2f, 0, l, gt, l, 0, 0, l, gt, h, 0, 0.5f)
    t.setNormal(0, -1, 0)
    t.addVertexWithUV(l, gt, h, 0, 1)
    t.addVertexWithUV(l, gt, l, 0, 0.5)
    t.addVertexWithUV(h, gt, l, 0.5, 0.5)
    t.addVertexWithUV(h, gt, h, 0.5, 1)
  }

  private def emitBottomPiece(t: Tessellator) {
    fanQuad(t, 0.03f, 0.75f, 0, -0.2f, -1, l, gb, l, 0.5f, 0, h, gb, l, 1, 0)
    fanQuad(t, 0.03f, 0.75f, 1, -0.2f, 0, h, gb, l, 1, 0, h, gb, h, 1, 0.5f)
    fanQuad(t, 0.03f, 0.75f, 0, -0.2f, 1, h, gb, h, 1, 0.5f, l, gb, h, 0.5f, 0.5f)
    fanQuad(t, 0.03f, 0.75f, -1, -0.2f, 0, l, gb, h, 0.5f, 0.5f, l, gb, l, 0.5f, 0)
    t.setNormal(0, 1, 0)
    t.addVertexWithUV(l, gb, l, 0, 0.5)
    t.addVertexWithUV(l, gb, h, 0, 1)
    t.addVertexWithUV(h, gb, h, 0.5, 1)
    t.addVertexWithUV(h, gb, l, 0.5, 0.5)
  }

  private val pieces = new Array[CapturedMesh](2)

  private[tileentity] def pieceMesh(top: Boolean): CapturedMesh = {
    var mesh = pieces(if (top) 1 else 0)
    if (mesh == null) {
      mesh = CapturedMesh.build(DefaultVertexFormat.POSITION_TEXTURE_NORMAL, Textures.blockRobot)(emitPiece(_, top))
      pieces(if (top) 1 else 0) = mesh
    }
    mesh
  }

  private def drawPiece(top: Boolean, robot: tileentity.Robot): Unit =
    if (robot == null) pieceMesh(top).draw()
    else MeshBackend.active.drawRobotPiece(top)

  def emitPiece(t: Tessellator, top: Boolean): Unit = if (top) emitTopPiece(t) else emitBottomPiece(t)

  def emitLight(t: Tessellator, step: Int) {
    val size = 0.3f
    val l = 0.5f - size
    val h = 0.5f + size
    val vStep = 1.0f / 32.0f
    val u0 = 0.5f
    val u1 = 1f
    val v0 = 0.5f + step * vStep
    val v1 = v0 + vStep

    t.addVertexWithUV(l, gt, l, u0, v0)
    t.addVertexWithUV(l, gb, l, u0, v1)
    t.addVertexWithUV(l, gb, h, u1, v1)
    t.addVertexWithUV(l, gt, h, u1, v0)

    t.addVertexWithUV(l, gt, h, u0, v0)
    t.addVertexWithUV(l, gb, h, u0, v1)
    t.addVertexWithUV(h, gb, h, u1, v1)
    t.addVertexWithUV(h, gt, h, u1, v0)

    t.addVertexWithUV(h, gt, h, u0, v0)
    t.addVertexWithUV(h, gb, h, u0, v1)
    t.addVertexWithUV(h, gb, l, u1, v1)
    t.addVertexWithUV(h, gt, l, u1, v0)

    t.addVertexWithUV(h, gt, l, u0, v0)
    t.addVertexWithUV(h, gb, l, u0, v1)
    t.addVertexWithUV(l, gb, l, u1, v1)
    t.addVertexWithUV(l, gt, l, u1, v0)
  }

  private def resetMountPoints(mountPoints: Array[RobotRenderEvent.MountPoint], running: Boolean) {
    val offset = if (running) 0 else -0.06f

    // Left top.
    mountPoints(0).offset.setX(0)
    mountPoints(0).offset.setY(0.2f)
    mountPoints(0).offset.setZ(0.24f)
    mountPoints(0).rotation.setX(0)
    mountPoints(0).rotation.setY(1)
    mountPoints(0).rotation.setZ(0)
    mountPoints(0).rotation.setW(90)

    // Right top.
    mountPoints(1).offset.setX(0)
    mountPoints(1).offset.setY(0.2f)
    mountPoints(1).offset.setZ(0.24f)
    mountPoints(1).rotation.setX(0)
    mountPoints(1).rotation.setY(1)
    mountPoints(1).rotation.setZ(0)
    mountPoints(1).rotation.setW(-90)

    // Back top.
    mountPoints(2).offset.setX(0)
    mountPoints(2).offset.setY(0.2f)
    mountPoints(2).offset.setZ(0.24f)
    mountPoints(2).rotation.setX(0)
    mountPoints(2).rotation.setY(1)
    mountPoints(2).rotation.setZ(0)
    mountPoints(2).rotation.setW(180)

    // Left bottom.
    mountPoints(3).offset.setX(0)
    mountPoints(3).offset.setY(-0.2f - offset)
    mountPoints(3).offset.setZ(0.24f)
    mountPoints(3).rotation.setX(0)
    mountPoints(3).rotation.setY(1)
    mountPoints(3).rotation.setZ(0)
    mountPoints(3).rotation.setW(90)

    // Right bottom.
    mountPoints(4).offset.setX(0)
    mountPoints(4).offset.setY(-0.2f - offset)
    mountPoints(4).offset.setZ(0.24f)
    mountPoints(4).rotation.setX(0)
    mountPoints(4).rotation.setY(1)
    mountPoints(4).rotation.setZ(0)
    mountPoints(4).rotation.setW(-90)

    // Back bottom.
    mountPoints(5).offset.setX(0)
    mountPoints(5).offset.setY(-0.2f - offset)
    mountPoints(5).offset.setZ(0.24f)
    mountPoints(5).rotation.setX(0)
    mountPoints(5).rotation.setY(1)
    mountPoints(5).rotation.setZ(0)
    mountPoints(5).rotation.setW(180)

    // Front bottom.
    mountPoints(6).offset.setX(0)
    mountPoints(6).offset.setY(-0.2f - offset)
    mountPoints(6).offset.setZ(0.24f)
    mountPoints(6).rotation.setX(0)
    mountPoints(6).rotation.setY(1)
    mountPoints(6).rotation.setZ(0)
    mountPoints(6).rotation.setW(0)
  }

  def renderChassis(robot: tileentity.Robot = null, offset: Double = 0, isRunningOverride: Boolean = false) {
    val slots = enter()
    try renderChassis(slots, robot, offset, isRunningOverride)
    finally exit()
  }

  private def renderChassis(slots: RenderSlots, robot: tileentity.Robot, offset: Double, isRunningOverride: Boolean) {
    val isRunning = if (robot == null) isRunningOverride else robot.isRunning
    resetMountPoints(slots.mountPoints, robot != null && robot.isRunning)
    val event = new RobotRenderEvent(robot, slots.mountPoints)
    MinecraftForge.EVENT_BUS.post(event)
    if (!event.isCanceled) {
      bindTexture(Textures.blockRobot)
      if (MinecraftForgeClient.getRenderPass > 0) {
        if (!isRunning) GL11.glTranslatef(0, -4 * gap, 0)
        return
      }
      if (!isRunning) {
        GL11.glTranslatef(0, -2 * gap, 0)
      }

      drawPiece(top = false, robot)
      if (!isRunning) {
        GL11.glTranslatef(0, -2 * gap, 0)
      }

      drawPiece(top = true, robot)
      GL11.glColor3f(1, 1, 1)

      if (isRunning) {
        val lightColor = if (robot != null && robot.info != null) robot.info.lightColor else 0xF23030
        val red = ((lightColor >>> 16) & 0xFF) / 255f
        val green = ((lightColor >>> 8) & 0xFF) / 255f
        val blue = (lightColor & 0xFF) / 255f
        val step = RobotLight.step(offset)

        if (robot != null) MeshBackend.active.drawRobotLight(step, red, green, blue)
        else {
          RenderState.disableLighting()
          GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE)
          GL11.glColor3f(red, green, blue)
          val t = Tessellator.instance
          t.startDrawingQuads()
          emitLight(t, step)
          t.draw()
          RenderState.enableLighting()
        }
      }
    }
  }

  private def hasUpgradeRenderer(robot: tileentity.Robot): Boolean = {
    val size = robot.getSizeInventory
    hasUpgradeRenderer(robot, size - robot.componentCount, size) ||
      hasUpgradeRenderer(robot, 1, robot.info.containers.length + 1)
  }

  private def hasUpgradeRenderer(robot: tileentity.Robot, from: Int, until: Int): Boolean = {
    var slot = from
    while (slot < until) {
      val stack = robot.getStackInSlot(slot)
      if (stack != null && stack.getItem.isInstanceOf[UpgradeRenderer]) return true
      slot += 1
    }
    false
  }

  override def renderTileEntityAt(entity: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    val slots = enter()
    try renderRobot(slots, entity, x, y, z, f)
    finally exit()
  }

  private def renderRobot(slots: RenderSlots, entity: TileEntity, x: Double, y: Double, z: Double, f: Float) {
    RenderState.checkError(this, ".renderTileEntityAt: entering (aka: wasntme)")

    val proxy = entity.asInstanceOf[tileentity.RobotProxy]
    val robot = proxy.robot
    val worldTime = entity.getWorldObj.getTotalWorldTime + f

    GL11.glPushMatrix()
    GL11.glPushAttrib(RenderState.DrawAttribBits)
    GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5)

    // If the move started while we were rendering and we have a reference to
    // the *old* proxy the robot would be rendered at the wrong position, so we
    // correct for the offset.
    if (robot.proxy != proxy) {
      GL11.glTranslated(robot.proxy.x - proxy.x, robot.proxy.y - proxy.y, robot.proxy.z - proxy.z)
    }

    if (robot.isAnimatingMove) {
      val remaining = (robot.animationTicksLeft - f) / robot.animationTicksTotal.toDouble
      val dx = robot.moveFromX - robot.x
      val dy = robot.moveFromY - robot.y
      val dz = robot.moveFromZ - robot.z
      GL11.glTranslated(dx * remaining, dy * remaining, dz * remaining)
    }

    val timeJitter = robot.hashCode ^ 0xFF
    val hover =
      if (robot.isRunning) (Math.sin(timeJitter + worldTime / 20.0) * 0.03).toFloat
      else -0.03f
    GL11.glTranslatef(0, hover, 0)

    GL11.glPushMatrix()

    GL11.glDepthMask(true)
    GL11.glEnable(GL11.GL_LIGHTING)
    GL11.glDisable(GL11.GL_BLEND)
    GL11.glColor4f(1, 1, 1, 1)

    if (robot.isAnimatingTurn) {
      val remaining = (robot.animationTicksLeft - f) / robot.animationTicksTotal.toDouble
      GL11.glRotated(90 * remaining, 0, robot.turnAxis, 0)
    }

    BlockTransforms.rotateYaw(robot.yaw)

    GL11.glTranslatef(-0.5f, -0.5f, -0.5f)

    val offset = timeJitter + worldTime / 20.0
    renderChassis(slots, robot, offset, isRunningOverride = false)

    if (!robot.renderingErrored && x * x + y * y + z * z < 24 * 24) {
      Option(robot.getStackInSlot(0)) match {
        case Some(stack) =>
          val itemRenderer = RenderManager.instance.itemRenderer

          GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
          GL11.glPushMatrix()
          try {
            // Copy-paste from player render code, with minor adjustments for
            // robot scale.

            GL11.glDisable(GL11.GL_CULL_FACE)
            GL11.glEnable(GL12.GL_RESCALE_NORMAL)

            GL11.glScalef(1, -1, -1)
            GL11.glTranslatef(0, -8 * 0.0625F - 0.0078125F, -0.5F)

            if (robot.isAnimatingSwing) {
              val wantedTicksPerCycle = 10
              val cycles = math.max(robot.animationTicksTotal / wantedTicksPerCycle, 1)
              val ticksPerCycle = robot.animationTicksTotal / cycles
              val remaining = (robot.animationTicksLeft - f) / ticksPerCycle.toDouble
              GL11.glRotatef((Math.sin((remaining - remaining.toInt) * Math.PI) * 45).toFloat, 1, 0, 0)
            }

            val customRenderer = MinecraftForgeClient.getItemRenderer(stack, EQUIPPED)
            val is3D = customRenderer != null && customRenderer.shouldUseRenderHelper(EQUIPPED, stack, BLOCK_3D)

            if (is3D || (stack.getItem.isInstanceOf[ItemBlock] && RenderBlocks.renderItemIn3d(Block.getBlockFromItem(stack.getItem).getRenderType))) {
              val scale = 0.375f
              GL11.glTranslatef(0, 0.1875f, -0.3125f)
              GL11.glRotatef(20, 1, 0, 0)
              GL11.glRotatef(45, 0, 1, 0)
              GL11.glScalef(-scale, -scale, scale)
            }
            else if (stack.getItem == Items.bow) {
              val scale = 0.375f
              GL11.glTranslatef(0, 0.2f, -0.2f)
              GL11.glRotatef(-10, 0, 1, 0)
              GL11.glScalef(scale, -scale, scale)
              GL11.glRotatef(-20, 1, 0, 0)
              GL11.glRotatef(45, 0, 1, 0)
            }
            else if (stack.getItem.isFull3D) {
              val scale = 0.375f
              if (stack.getItem.shouldRotateAroundWhenRendering) {
                GL11.glRotatef(180, 0, 0, 1)
                GL11.glTranslatef(0, -0.125f, 0)
              }
              GL11.glTranslatef(0, 0.1f, 0)
              GL11.glScalef(scale, -scale, scale)
              GL11.glRotatef(-100, 1, 0, 0)
              GL11.glRotatef(45, 0, 1, 0)
            }
            else {
              val scale = 0.375f
              GL11.glTranslatef(0.25f, 0.1875f, -0.1875f)
              GL11.glScalef(scale, scale, scale)
              GL11.glRotatef(60, 0, 0, 1)
              GL11.glRotatef(-90, 1, 0, 0)
              GL11.glRotatef(20, 0, 0, 1)
            }

            val pass = MinecraftForgeClient.getRenderPass
            def renderPass(): Unit = {
              val tint = stack.getItem.getColorFromItemStack(stack, pass)
              val r = ((tint >> 16) & 0xFF) / 255f
              val g = ((tint >> 8) & 0xFF) / 255f
              val b = ((tint >> 0) & 0xFF) / 255f
              GL11.glColor4f(r, g, b, 1)
              itemRenderer.renderItem(Minecraft.getMinecraft.thePlayer, stack, pass)
            }

            if (stack.getItem.requiresMultipleRenderPasses()) {
              val passes = stack.getItem.getRenderPasses(stack.getItemDamage)
              if (pass < passes) {
                renderPass()
              }
              // Tile entities only get two render passes, so if items need
              // more, we have to fake them.
              if (pass == 1 && passes > 2) {
                for (fakePass <- 2 until passes) {
                  renderPass()
                }
              }
            }
            else if (pass == 0) {
              renderPass()
            }
          }
          catch {
            case e: Throwable =>
              OpenComputers.log.warn("Failed rendering equipped item.", e)
              robot.renderingErrored = true
          }
          GL11.glEnable(GL11.GL_CULL_FACE)
          GL11.glDisable(GL12.GL_RESCALE_NORMAL)
          GL11.glPopMatrix()
          GL11.glPopAttrib()
        case _ =>
      }

      if (MinecraftForgeClient.getRenderPass == 0 && hasUpgradeRenderer(robot)) {
        slots.availableSlots.clear()
        var nameIndex = 0
        while (nameIndex < mountPointNames.length) {
          slots.availableSlots.add(mountPointNames(nameIndex))
          nameIndex += 1
        }
        try {
          val size = robot.getSizeInventory
          var slot = size - robot.componentCount
          var phase = 0
          var last = size
          while (phase < 2) {
            while (slot < last) {
              val stack = robot.getStackInSlot(slot)
              if (stack != null && stack.getItem.isInstanceOf[UpgradeRenderer]) {
                val renderer = stack.getItem.asInstanceOf[UpgradeRenderer]
                val preferredSlot = renderer.computePreferredMountPoint(stack, robot, slots.availableSlots)
                if (slots.availableSlots.remove(preferredSlot)) {
                  val index = slotIndices.get(preferredSlot).intValue
                  slots.mountStacks(index) = stack
                  slots.mountRenderers(index) = renderer
                }
                else if (preferredSlot == MountPointName.Any) {
                  if (slots.wildcardCount == slots.wildcardStacks.length) {
                    slots.wildcardStacks = util.Arrays.copyOf(slots.wildcardStacks, slots.wildcardCount * 2)
                    slots.wildcardRenderers = util.Arrays.copyOf(slots.wildcardRenderers, slots.wildcardCount * 2)
                  }
                  slots.wildcardStacks(slots.wildcardCount) = stack
                  slots.wildcardRenderers(slots.wildcardCount) = renderer
                  slots.wildcardCount += 1
                }
              }
              slot += 1
            }
            phase += 1
            slot = 1
            last = robot.info.containers.length + 1
          }

          var firstEmpty = 0
          var wildcard = 0
          while (wildcard < slots.wildcardCount) {
            while (firstEmpty < slots.mountStacks.length && slots.mountStacks(firstEmpty) != null) firstEmpty += 1
            if (firstEmpty >= slots.mountStacks.length) wildcard = slots.wildcardCount
            else {
              slots.mountStacks(firstEmpty) = slots.wildcardStacks(wildcard)
              slots.mountRenderers(firstEmpty) = slots.wildcardRenderers(wildcard)
              wildcard += 1
            }
          }

          GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
          var mount = 0
          while (mount < slots.mountStacks.length) {
            val stack = slots.mountStacks(mount)
            if (stack != null) try {
              GL11.glPushMatrix()
              GL11.glTranslatef(0.5f, 0.5f, 0.5f)
              slots.mountRenderers(mount).render(stack, slots.mountPoints(mount), robot, f)
              GL11.glPopMatrix()
            }
            catch {
              case e: Throwable =>
                OpenComputers.log.warn("Failed rendering equipped upgrade.", e)
                robot.renderingErrored = true
            }
            mount += 1
          }
          GL11.glPopAttrib()
        }
        finally slots.clear()
      }
    }
    GL11.glPopMatrix()

    val name = robot.name
    if (Settings.get.robotLabels && MinecraftForgeClient.getRenderPass == 1 && !Strings.isNullOrEmpty(name) && x * x + y * y + z * z < RendererLivingEntity.NAME_TAG_RANGE) {
      GL11.glPushMatrix()

      // This is pretty much copy-pasta from the entity's label renderer.
      val t = Tessellator.instance
      val f = func_147498_b
      val scale = 1.6f / 60f
      val width = f.getStringWidth(name)
      val halfWidth = width / 2

      GL11.glTranslated(0, 0.8, 0)
      GL11.glNormal3f(0, 1, 0)
      GL11.glColor3f(1, 1, 1)

      GL11.glRotatef(-field_147501_a.field_147562_h, 0, 1, 0)
      GL11.glRotatef(field_147501_a.field_147563_i, 1, 0, 0)
      GL11.glScalef(-scale, -scale, scale)

      RenderState.makeItBlend()
      GL11.glDepthMask(false)
      GL11.glDisable(GL11.GL_LIGHTING)
      GL11.glDisable(GL11.GL_TEXTURE_2D)

      t.startDrawingQuads()
      t.setColorRGBA_F(0, 0, 0, 0.5f)
      t.addVertex(-halfWidth - 1, -1, 0)
      t.addVertex(-halfWidth - 1, 8, 0)
      t.addVertex(halfWidth + 1, 8, 0)
      t.addVertex(halfWidth + 1, -1, 0)
      t.draw

      GL11.glEnable(GL11.GL_TEXTURE_2D) // For the font.
      f.drawString(if (EventHandler.isItTime) EnumChatFormatting.OBFUSCATED.toString + name else name, -halfWidth, 0, 0xFFFFFFFF)

      GL11.glDepthMask(true)
      GL11.glEnable(GL11.GL_LIGHTING)
      GL11.glDisable(GL11.GL_BLEND)

      GL11.glPopMatrix()
    }

    GL11.glPopMatrix()
    GL11.glPopAttrib()

    RenderState.checkError(this, ".renderTileEntityAt: leaving")
  }
}
