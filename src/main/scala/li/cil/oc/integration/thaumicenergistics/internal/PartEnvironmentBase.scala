package li.cil.oc.integration.thaumicenergistics.internal

import appeng.api.storage.data.IAEStack
import appeng.parts.automation.PartSharedItemBus
import appeng.tile.inventory.IIAEStackInventory
import li.cil.oc.api.machine.{Arguments, Context}
import li.cil.oc.integration.appeng.AEStackFactory
import li.cil.oc.integration.appeng.internal.{PartEnvironmentBase, PartSharedItemBusBase}
import li.cil.oc.util.ExtendedArguments.extendedArguments
import li.cil.oc.util.ResultWrapper.result
import thaumicenergistics.common.storage.AEEssentiaStack

import scala.collection.convert.WrapAsJava.mapAsJavaMap
import scala.reflect.ClassTag

trait PartEssentiaBusBase[PartType <: PartSharedItemBus[AEEssentiaStack]] extends PartSharedItemBusBase[PartType] {
  implicit def tag: ClassTag[PartType]
}

object PartEssentiaBusBase {
  implicit class ConfigOps[PartType <: PartSharedItemBus[AEEssentiaStack]](val env: PartEnvironmentBase[PartType]) {
    def setPartConfig[T <: AEEssentiaStack](context: Context, args: Arguments)(implicit ev: PartType <:< IIAEStackInventory): Array[AnyRef] = {
      val side = args.checkSideAny(0)
      val part = env.getPart(side)
      val (slot, offset) = if (args.isInteger(1)) (args.checkInteger(1), 2) else (0, 1)
      val stack = if (args.isTable(offset)) AEStackFactory.parse[AEEssentiaStack](args.checkTable(offset))
      else if (args.isString(offset)) AEStackFactory.parse[AEEssentiaStack](mapAsJavaMap(Map("name" -> args.checkString(offset))))
      else null.asInstanceOf[AEEssentiaStack]
      env.setPartConfigInternal(part, slot, stack)(ev)
      result(true)
    }
  }
}
