package li.cil.oc.integration.fmp

import codechicken.multipart.IconHitEffects
import codechicken.multipart.TIconHitEffects
import codechicken.multipart.TMultiPart
import cpw.mods.fml.relauncher.Side
import cpw.mods.fml.relauncher.SideOnly
import li.cil.oc.common.block.SimpleBlock
import net.minecraft.client.particle.EffectRenderer
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.util.MovingObjectPosition
import net.minecraftforge.common.util.ForgeDirection

import scala.collection.convert.WrapAsJava

abstract class SimpleBlockPart extends TMultiPart with TIconHitEffects {
  def simpleBlock: SimpleBlock

  override def pickItem(hit: MovingObjectPosition) = new ItemStack(simpleBlock)

  override def getDrops = WrapAsJava.asJavaIterable(Iterable(new ItemStack(simpleBlock)))

  override def explosionResistance(entity: Entity) = simpleBlock.getExplosionResistance(entity)

  @SideOnly(Side.CLIENT)
  override def getBrokenIcon(side: Int) = simpleBlock.getIcon(ForgeDirection.getOrientation(side), 0)

  @SideOnly(Side.CLIENT)
  override def addHitEffects(hit: MovingObjectPosition, effectRenderer: EffectRenderer): Unit = IconHitEffects.addHitEffects(this, hit, effectRenderer)

  @SideOnly(Side.CLIENT)
  override def addDestroyEffects(hit: MovingObjectPosition, effectRenderer: EffectRenderer): Unit = IconHitEffects.addDestroyEffects(this, effectRenderer)
}