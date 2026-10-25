package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.client.renderer.Blink
import li.cil.oc.client.renderer.UnitQuad
import li.cil.oc.client.renderer.shading.Blend
import li.cil.oc.common.tileentity
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.util.ForgeDirection
import org.joml.Matrix4fc

object Overlays {
  private val Atlas = TextureMap.locationBlocksTexture

  private val Front = new FrontTransforms(0.505f)

  private val ChargerFront = new FrontTransforms(0.5f)

  private final val AllSides = CubeFaces.Walls | ForgeDirection.DOWN.flag | ForgeDirection.UP.flag

  val Geolyzer: Overlay = new Overlay(1, Blend.Additive) {
    override def transform(te: TileEntity): Matrix4fc = BlockTransforms.Inflated

    override def emit(sink: OverlaySink, state: Int): Unit = CubeFaces.emitTop(sink.bucket(Atlas), Textures.Geolyzer.iconTopOn)
  }

  val Case: Overlay = new Overlay(OverlayState.ComputerStates, Blend.Additive) {
    override def state(te: TileEntity): Int = {
      val computer = te.asInstanceOf[tileentity.Case]
      val running = computer.isRunning
      OverlayState.computer(running,
        running && Blink.activity(computer.lastFileSystemAccess, computer.world.rand),
        computer.hasErrored && Blink.shouldShowErrorLight(computer.hashCode))
    }

    override def transform(te: TileEntity): Matrix4fc = Front(te.asInstanceOf[tileentity.Case].yaw)

    override def emit(sink: OverlaySink, state: Int) {
      if ((state & OverlayState.ComputerRunning) != 0) UnitQuad.emitFrontQuad(sink.bucket(Textures.blockCaseFrontOn))
      if ((state & OverlayState.ComputerActivity) != 0) UnitQuad.emitFrontQuad(sink.bucket(Textures.blockCaseFrontActivity))
      if ((state & OverlayState.ComputerError) != 0) UnitQuad.emitFrontQuad(sink.bucket(Textures.blockCaseFrontError))
    }
  }

  val Microcontroller: Overlay = new Overlay(OverlayState.MicrocontrollerStates, Blend.Additive) {
    override def state(te: TileEntity): Int = {
      val mcu = te.asInstanceOf[tileentity.Microcontroller]
      OverlayState.microcontroller(mcu.isRunning, mcu.hasErrored && Blink.shouldShowErrorLight(mcu.hashCode))
    }

    override def transform(te: TileEntity): Matrix4fc = Front(te.asInstanceOf[tileentity.Microcontroller].yaw)

    override def emit(sink: OverlaySink, state: Int) {
      UnitQuad.emitFrontQuad(sink.bucket(Textures.blockMicrocontrollerFrontLight))
      if (state == OverlayState.MicrocontrollerRunning) UnitQuad.emitFrontQuad(sink.bucket(Textures.blockMicrocontrollerFrontOn))
      if (state == OverlayState.MicrocontrollerError) UnitQuad.emitFrontQuad(sink.bucket(Textures.blockMicrocontrollerFrontError))
    }
  }

  val Raid: Overlay = new Overlay(OverlayState.RaidStates, Blend.Alpha) {
    override def state(te: TileEntity): Int = {
      val raid = te.asInstanceOf[tileentity.Raid]
      val slots = math.min(raid.getSizeInventory, OverlayState.RaidSlotBits)
      val active = slots > 0 && Blink.activity(raid.lastAccess, raid.world.rand)
      OverlayState.raidState(raid.presence, slots, raid.lastAccess, active)
    }

    override def transform(te: TileEntity): Matrix4fc = Front(te.asInstanceOf[tileentity.Raid].yaw)

    override def emit(sink: OverlaySink, state: Int) {
      val missing = OverlayState.raidMissingSlots(state)
      val activeSlot = OverlayState.raidActiveSlot(state)
      if (missing != 0) {
        val t = sink.bucket(Textures.blockRaidFrontError)
        var slot = 0
        while (slot < OverlayState.RaidSlotBits) {
          if ((missing & (1 << slot)) != 0) emitRaidSlot(t, slot)
          slot += 1
        }
      }
      if (OverlayState.raidActivityVisible(state)) emitRaidSlot(sink.bucket(Textures.blockRaidFrontActivity), activeSlot)
    }
  }

  val Assembler: Overlay = new Overlay(2, Blend.Additive) {
    override def state(te: TileEntity): Int = if (te.asInstanceOf[tileentity.Assembler].isAssembling) 1 else 0

    override def transform(te: TileEntity): Matrix4fc = BlockTransforms.Centered

    override def emit(sink: OverlaySink, state: Int): Unit = emitAssembler(sink.bucket(Atlas), state != 0)
  }

  val Charger: Overlay = new Overlay(OverlayState.ChargerStates, Blend.Additive) {
    override def state(te: TileEntity): Int = {
      val charger = te.asInstanceOf[tileentity.Charger]
      OverlayState.charger(charger.chargeSpeed, charger.hasPower)
    }

    override def transform(te: TileEntity): Matrix4fc = ChargerFront(te.asInstanceOf[tileentity.Charger].yaw)

    override def emit(sink: OverlaySink, state: Int): Unit =
      emitCharger(sink.bucket(Atlas), OverlayState.chargerSpeed(state), OverlayState.chargerPowered(state))
  }

  val Switch: Overlay = new Overlay(1, Blend.Additive, animated = true) {
    override def alpha(te: TileEntity): Float =
      1 - (System.currentTimeMillis() - te.asInstanceOf[tileentity.traits.SwitchLike].lastMessage) / 1000f

    override def transform(te: TileEntity): Matrix4fc = BlockTransforms.Inflated

    override def emit(sink: OverlaySink, state: Int): Unit = CubeFaces.emitSides(sink.bucket(Atlas), Textures.Switch.iconSideActivity, CubeFaces.Walls)
  }

  val Transposer: Overlay = new Overlay(1, Blend.Additive, animated = true) {
    override def alpha(te: TileEntity): Float =
      1 - (System.currentTimeMillis() - te.asInstanceOf[tileentity.Transposer].lastOperation) / 1000f

    override def transform(te: TileEntity): Matrix4fc = BlockTransforms.Inflated

    override def emit(sink: OverlaySink, state: Int): Unit = CubeFaces.emitSides(sink.bucket(Atlas), Textures.Transposer.iconOn, AllSides)
  }

  val PowerDistributor: Overlay = new Overlay(1, Blend.Additive) {
    // Not animated: alpha is the glColor, not mesh data.
    override def alpha(te: TileEntity): Float = {
      val distributor = te.asInstanceOf[tileentity.PowerDistributor]
      if (distributor.globalBuffer > 0) (distributor.globalBuffer / distributor.globalBufferSize).toFloat else 0f
    }

    override def transform(te: TileEntity): Matrix4fc = BlockTransforms.Inflated

    override def emit(sink: OverlaySink, state: Int) {
      val t = sink.bucket(Atlas)
      CubeFaces.emitTop(t, Textures.PowerDistributor.iconTopOn)
      CubeFaces.emitSides(t, Textures.PowerDistributor.iconSideOn, CubeFaces.Walls)
    }
  }

  val DiskDriveActivity: Overlay = new Overlay(1, Blend.Additive) {
    override def state(te: TileEntity): Int = {
      val drive = te.asInstanceOf[tileentity.DiskDrive]
      if (Blink.activity(drive.lastAccess, drive.world.rand)) 0 else OverlayState.NoMesh
    }

    override def transform(te: TileEntity): Matrix4fc = Front(te.asInstanceOf[tileentity.DiskDrive].yaw)

    override def emit(sink: OverlaySink, state: Int): Unit = UnitQuad.emitFrontQuad(sink.bucket(Textures.blockDiskDriveFrontActivity))
  }

  private final val RaidInset = 2 / 16f

  private final val RaidSlotWidth = 4 / 16f

  private def emitRaidSlot(t: Tessellator, slot: Int) {
    val l = RaidInset + slot * RaidSlotWidth
    val h = RaidInset + (slot + 1) * RaidSlotWidth
    t.addVertexWithUV(l, 1, 0, l, 1)
    t.addVertexWithUV(h, 1, 0, h, 1)
    t.addVertexWithUV(h, 0, 0, h, 0)
    t.addVertexWithUV(l, 0, 0, l, 0)
  }

  private def emitQuarterTurned(t: Tessellator, turns: Int, x: Double, y: Double, z: Double, u: Double, v: Double) {
    var rx = x
    var rz = z
    var i = 0
    while (i < turns) {
      val nx = rz
      rz = -rx
      rx = nx
      i += 1
    }
    t.addVertexWithUV(rx, y, rz, u, v)
  }

  private def emitAssembler(t: Tessellator, assembling: Boolean) {
    CubeFaces.emitTop(t, Textures.Assembler.iconTopOn, -0.5, 0.55, -0.5, 0.5, 0.5)

    val indent = 6 / 16f + 0.005
    val working = Textures.Assembler.iconSideAssembling
    val side = Textures.Assembler.iconSideOn
    var turns = 0
    while (turns < 4) {
      if (assembling) {
        emitQuarterTurned(t, turns, indent, 0.5, -indent, working.getInterpolatedU((0.5 - indent) * 16), working.getMaxV)
        emitQuarterTurned(t, turns, indent, 0.5, indent, working.getInterpolatedU((0.5 + indent) * 16), working.getMaxV)
        emitQuarterTurned(t, turns, indent, -0.5, indent, working.getInterpolatedU((0.5 + indent) * 16), working.getMinV)
        emitQuarterTurned(t, turns, indent, -0.5, -indent, working.getInterpolatedU((0.5 - indent) * 16), working.getMinV)
      }
      emitQuarterTurned(t, turns, 0.5005, 0.5, -0.5, side.getMinU, side.getMaxV)
      emitQuarterTurned(t, turns, 0.5005, 0.5, 0.5, side.getMaxU, side.getMaxV)
      emitQuarterTurned(t, turns, 0.5005, -0.5, 0.5, side.getMaxU, side.getMinV)
      emitQuarterTurned(t, turns, 0.5005, -0.5, -0.5, side.getMinU, side.getMinV)
      turns += 1
    }
  }

  private def emitCharger(t: Tessellator, chargeSpeed: Double, hasPower: Boolean) {
    val front = Textures.Charger.iconFrontCharging
    val inverse = 1 - chargeSpeed
    t.addVertexWithUV(0, 1, 0.005, front.getMinU, front.getMaxV)
    t.addVertexWithUV(1, 1, 0.005, front.getMaxU, front.getMaxV)
    t.addVertexWithUV(1, inverse, 0.005, front.getMaxU, front.getInterpolatedV(inverse * 16))
    t.addVertexWithUV(0, inverse, 0.005, front.getMinU, front.getInterpolatedV(inverse * 16))

    if (hasPower) {
      val side = Textures.Charger.iconSideCharging
      t.addVertexWithUV(-0.005, 1, -1, side.getMinU, side.getMaxV)
      t.addVertexWithUV(-0.005, 1, 0, side.getMaxU, side.getMaxV)
      t.addVertexWithUV(-0.005, 0, 0, side.getMaxU, side.getMinV)
      t.addVertexWithUV(-0.005, 0, -1, side.getMinU, side.getMinV)

      t.addVertexWithUV(1, 1, -1.005, side.getMinU, side.getMaxV)
      t.addVertexWithUV(0, 1, -1.005, side.getMaxU, side.getMaxV)
      t.addVertexWithUV(0, 0, -1.005, side.getMaxU, side.getMinV)
      t.addVertexWithUV(1, 0, -1.005, side.getMinU, side.getMinV)

      t.addVertexWithUV(1.005, 1, 0, side.getMinU, side.getMaxV)
      t.addVertexWithUV(1.005, 1, -1, side.getMaxU, side.getMaxV)
      t.addVertexWithUV(1.005, 0, -1, side.getMaxU, side.getMinV)
      t.addVertexWithUV(1.005, 0, 0, side.getMinU, side.getMinV)
    }
  }
}
