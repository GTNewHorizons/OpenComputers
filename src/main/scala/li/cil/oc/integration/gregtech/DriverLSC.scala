package li.cil.oc.integration.gregtech

import gregtech.api.interfaces.tileentity.IGregTechTileEntity
import kekztech.common.tileentities.MTELapotronicSuperCapacitor
import li.cil.oc.api.driver.{NamedBlock, SidedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

object DriverLSC extends SidedBlock {
  override def worksWith(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean = {
    world.getTileEntity(x, y, z) match {
      case tile: IGregTechTileEntity => tile.getMetaTileEntity.isInstanceOf[MTELapotronicSuperCapacitor]
      case _ => false
    }
  }

  override def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment = {
    val tile = world.getTileEntity(x, y, z).asInstanceOf[IGregTechTileEntity]
    new Environment(tile.getMetaTileEntity.asInstanceOf[MTELapotronicSuperCapacitor])
  }

  final class Environment(tile: MTELapotronicSuperCapacitor) extends ManagedTileEntityEnvironment[MTELapotronicSuperCapacitor](tile, "LSC") with NamedBlock {
    override def preferredName(): String = "LSC"

    override def priority(): Int = 0

    @Callback(doc = "function():number --  Returns the amount of electricity contained in this Block, in EU units! NOTE: Value is clamped to Long.MaxValue to prevent overflow.")
    def getStoredEU(context: Context, args: Arguments): Array[AnyRef] = result(tileEntity.getEUVar)

    @Callback(doc = "function():string --  Returns the amount of electricity contained in this Block, in EU units! (As a string for HUGE amounts.)")
    def getStoredEUString(context: Context, args: Arguments) = Array[AnyRef](tileEntity.getStored.toString)

    @Callback(doc = "function():number --  Returns the amount of electricity containable in this Block, in EU units! NOTE: Value is clamped to Long.MaxValue to prevent overflow.")
    def getEUCapacity(context: Context, args: Arguments): Array[AnyRef] = result(tileEntity.maxEUStore)

    @Callback(doc = "function():number --  Returns the amount of electricity containable in this Block, in EU units! (As a string for HUGE amounts.)")
    def getEUCapacityString(context: Context, args: Arguments) = Array[AnyRef](tileEntity.getEnergyCapacity.toString)
  }
}
