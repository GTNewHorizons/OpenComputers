package li.cil.oc.common.block

import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.Settings
import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity
import li.cil.oc.common.item.data.TransposerData.FLUID_TRANSFER_RATE
import net.minecraft.client.renderer.texture.IIconRegister
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.MovingObjectPosition
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

import java.text.NumberFormat
import java.util
import scala.reflect.ClassTag

class Transposer(protected implicit val tileTag: ClassTag[tileentity.Transposer]) extends SimpleBlock with traits.CustomDrops[tileentity.Transposer] {
  override protected def customTextures = Array(
    Some("TransposerTop"),
    Some("TransposerTop"),
    Some("TransposerSide"),
    Some("TransposerSide"),
    Some("TransposerSide"),
    Some("TransposerSide")
  )

  @SideOnly(Side.CLIENT)
  override def registerBlockIcons(iconRegister: IIconRegister): Unit = {
    super.registerBlockIcons(iconRegister)
    Textures.Transposer.iconOn = iconRegister.registerIcon(Settings.resourceDomain + ":TransposerOn")
  }

  override def isSideSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection): Boolean = false

  // ----------------------------------------------------------------------- //

  override def hasTileEntity(metadata: Int) = true

  override def createTileEntity(world: World, metadata: Int) = new tileentity.Transposer()

  override def getPickBlock(target: MovingObjectPosition, world: World, x: Int, y: Int, z: Int, entityPlayer: EntityPlayer) =
    world.getTileEntity(x, y, z) match {
      case transposer: tileentity.Transposer => transposer.info.copyItemStack()
      case _ => null
    }

  override protected def doCustomInit(tileEntity: tileentity.Transposer, player: EntityLivingBase, stack: ItemStack): Unit = {
    super.doCustomInit(tileEntity, player, stack)
    if (!tileEntity.world.isRemote) {
      tileEntity.info.load(stack)
    }
  }

  override protected def doCustomDrops(tileEntity: tileentity.Transposer, player: EntityPlayer, willHarvest: Boolean): Unit = {
    super.doCustomDrops(tileEntity, player, willHarvest)
    dropBlockAsItem(tileEntity.world, tileEntity.x, tileEntity.y, tileEntity.z, tileEntity.info.createItemStack())
  }

  override protected def tooltipBody(metadata: Int, stack: ItemStack, player: EntityPlayer, tooltip: util.List[String], advanced: Boolean): Unit = {
    val tag = stack.getTagCompound
    val transferRate =
      if (tag != null && tag.hasKey(FLUID_TRANSFER_RATE))
        tag.getInteger(FLUID_TRANSFER_RATE)
      else
        Settings.get.transposerFluidTransferRate

    tooltip.add(s"Transfers up to ${NumberFormat.getIntegerInstance.format(transferRate)}L/s.")
    super.tooltipBody(metadata, stack, player, tooltip, advanced)
  }
}
