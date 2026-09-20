package li.cil.oc.util

import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.server.component.UpgradeDatabase
import li.cil.oc.api.machine.Arguments
import net.minecraft.item.ItemStack

object DatabaseAccess {
  def withDatabase(node: Node, address: String, f: UpgradeDatabase => Array[AnyRef]): Array[AnyRef] = {
    withDatabaseGeneric[Array[AnyRef]](node, address, f)
  }

  def withDatabaseGeneric[T](node: Node, address: String, f: UpgradeDatabase => T): T = {
    node.network.node(address) match {
      case component: Component => component.host match {
        case database: UpgradeDatabase => f(database)
        case _ => throw new IllegalArgumentException("not a database")
      }
      case _ => throw new IllegalArgumentException("no such component")
    }
  }

  def getStackFromDatabase(node: Node, args: Arguments, offset: Int): ItemStack = {
    if (args.isString(offset)) DatabaseAccess.withDatabaseGeneric[ItemStack](node, args.checkString(offset), database => {
      val entry = args.checkInteger(offset + 1)
      val size = args.optInteger(offset + 2, 1)
      val dbStack = database.getStackInSlot(entry - 1)
      if (dbStack == null || size < 1) null
      else {
        dbStack.stackSize = math.min(size, dbStack.getMaxStackSize)
        dbStack
      }
    })
    else null
  }
}
