package li.cil.oc.integration.appeng

import appeng.api.config.{CopyMode, Settings}
import appeng.api.storage.StorageName
import appeng.helpers.ICellRestriction.CellRestrictionData
import appeng.tile.misc.TileCellWorkbench
import li.cil.oc.api.driver.{EnvironmentProvider, NamedBlock}
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.common.util.ForgeDirection

object DriverCellWorkbench extends DriverSidedTileEntity {
  def getTileEntityClass = classOf[TileCellWorkbench]

  def createEnvironment(world: World, x: Int, y: Int, z: Int, side: ForgeDirection): ManagedEnvironment =
    new Environment(world.getTileEntity(x, y, z).asInstanceOf[TileCellWorkbench])

  final class Environment(val tile: TileCellWorkbench)
      extends ManagedTileEntityEnvironment[TileCellWorkbench](tile, "me_cellworkbench") with NamedBlock {
    override def preferredName = "me_cellworkbench"

    override def priority = 5

    private def config = tile.getAEInventoryByName(StorageName.CONFIG)

    private def checkSlot(slot: Int): Int = {
      if (slot < 0 || slot >= config.getSizeInventory) {
        throw new IllegalArgumentException("invalid slot")
      }
      slot
    }

    // tile.getStackType() is cached and only updates on cell insert/remove/swap
    // Server restart resets to null even with cell inserted
    private def liveStackTypeId: Option[String] = {
      val cell = tile.getCell
      if (cell == null) None
      else {
        val stackType = cell.getStackType
        if (stackType == null) None else Some(stackType.getId)
      }
    }

    @Callback(doc = "function():boolean -- Returns whether a storage cell is currently inserted.")
    def hasCell(context: Context, args: Arguments): Array[AnyRef] = result(tile.getCell != null)

    @Callback(doc = "function():string -- Returns the inserted cell's type (\"item\", \"fluid\", \"essentia\", ...). Returns nil if no cell is inserted.")
    def getCellType(context: Context, args: Arguments): Array[AnyRef] = result(liveStackTypeId.orNull)

    @Callback(doc = "function():table -- Returns details about the inserted cell item itself. Returns nil if no cell is inserted. Essentia cells return limited info.")
    def getCell(context: Context, args: Arguments): Array[AnyRef] =
      result(tile.getInventoryByName("cell").getStackInSlot(0))

    @Callback(doc = "function():table -- Returns every partition slot as a table keyed by slot number (1-based). Empty slots are omitted.")
    def getPartition(context: Context, args: Arguments): Array[AnyRef] =
      result(Array.tabulate(config.getSizeInventory)(config.getAEStackInSlot))

    @Callback(doc = "function(slot:number[, item:table]):boolean -- Sets the partition in the given slot (1-based). Accepts a table describing the item (e.g. name, damage). Omit the item to clear the slot.")
    def setPartition(context: Context, args: Arguments): Array[AnyRef] = {
      val slot = checkSlot(args.checkInteger(0) - 1)
      val stack =
        if (args.count <= 1) null
        else {
          val stackTypeId = liveStackTypeId.getOrElse(throw new IllegalArgumentException("no cell inserted"))
          AEStackFactory.parse(stackTypeId, args.checkTable(1))
        }
      config.putAEStackInSlot(slot, stack)
      tile.saveAEStackInv()
      result(true)
    }

    @Callback(doc = "function():boolean -- Clears every partition slot on the inserted cell.")
    def clearPartitions(context: Context, args: Arguments): Array[AnyRef] = {
      for (i <- 0 until config.getSizeInventory) config.putAEStackInSlot(i, null)
      tile.saveAEStackInv()
      result(true)
    }

    @Callback(doc = "function():number, number -- Returns the cell's restriction as (types, amount). (0, 0) means unrestricted.")
    def getRestriction(context: Context, args: Arguments): Array[AnyRef] = {
      val r = tile.getCellRestrictionData(null)
      if (r == null) result(0, 0) else result(r.restrictionTypes, r.restrictionAmount)
    }

    @Callback(doc = "function(types:number, amount:number):boolean -- Sets the cell's restriction. (0, 0) means unrestricted.")
    def setRestriction(context: Context, args: Arguments): Array[AnyRef] = {
      if (tile.getCell == null) throw new IllegalArgumentException("no cell inserted")
      val types = args.checkInteger(0)
      val amount = args.checkLong(1)
      tile.setCellRestriction(null, new CellRestrictionData(types.toByte, amount))
      result(true)
    }

    @Callback(doc = "function():string -- Returns the inserted cell's ore filter string. Returns empty string (not nil) if unset.")
    def getOreFilter(context: Context, args: Arguments): Array[AnyRef] = result(tile.getFilter)

    @Callback(doc = "function(filter:string):boolean -- Sets the inserted cell's ore filter string.")
    def setOreFilter(context: Context, args: Arguments): Array[AnyRef] = {
      if (tile.getCell == null) throw new IllegalArgumentException("no cell inserted")
      tile.setFilter(args.checkString(0))
      result(true)
    }

    @Callback(doc = "function():string -- Returns the workbench's copy mode: \"clear\" or \"keep\" (whether the partitions are cleared or remains when a cell is removed).")
    def getCopyMode(context: Context, args: Arguments): Array[AnyRef] = {
      val mode = tile.getConfigManager.getSetting(Settings.COPY_MODE)
      result(if (mode == CopyMode.KEEP_ON_REMOVE) "keep" else "clear")
    }

    @Callback(doc = "function(mode:string):boolean -- Sets the workbench's copy mode: \"clear\" or \"keep\".")
    def setCopyMode(context: Context, args: Arguments): Array[AnyRef] = {
      val mode = args.checkString(0) match {
        case "clear" => CopyMode.CLEAR_ON_REMOVE
        case "keep" => CopyMode.KEEP_ON_REMOVE
        case other => throw new IllegalArgumentException(s"invalid mode: $other")
      }
      tile.getConfigManager.putSetting(Settings.COPY_MODE, mode)
      result(true)
    }
  }

  object Provider extends EnvironmentProvider {
    override def getEnvironment(stack: ItemStack): Class[_] =
      if (AEUtil.isCellWorkbench(stack)) classOf[Environment] else null
  }
}
