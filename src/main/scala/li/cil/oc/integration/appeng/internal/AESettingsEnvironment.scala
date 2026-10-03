package li.cil.oc.integration.appeng.internal

import appeng.api.config.{Settings, YesNo}
import appeng.api.util.IConfigManager
import appeng.parts.AEBasePart
import appeng.tile.AEBaseTile
import li.cil.oc.api.machine.{Arguments, Callback, Context}
import li.cil.oc.api.network.Environment
import li.cil.oc.util.ResultWrapper.result

import java.util.Locale

trait AESettingsEnvironment extends Environment {
  protected def settingsTarget(context: Context, args: Arguments): (IConfigManager, AnyRef, Int)

  private def possibleValues(setting: Settings): Vector[Enum[_]] = {
    val values = Vector.newBuilder[Enum[_]]
    val iterator = setting.getPossibleValues.iterator()
    while (iterator.hasNext) {
      values += iterator.next().asInstanceOf[Enum[_]]
    }
    values.result()
  }

  private def listValues(setting: Settings): String =
    possibleValues(setting).map(_.name).mkString(", ")

  private def isRegistered(manager: IConfigManager, setting: Settings): Boolean =
    manager.getSettings.contains(setting)

  protected def readSetting(setting: Settings, context: Context, args: Arguments): Array[AnyRef] = {
    val (manager, _, _) = settingsTarget(context, args)
    if (!isRegistered(manager, setting)) {
      result(null, s"setting ${setting.name} is not registered on this component")
    }
    else {
      manager.getSetting(setting) match {
        case value: YesNo => result(value == YesNo.YES)
        case value => result(value.name)
      }
    }
  }

  protected def writeSetting(setting: Settings, context: Context, args: Arguments): Array[AnyRef] = {
    val (manager, target, valueIndex) = settingsTarget(context, args)
    if (!isRegistered(manager, setting)) {
      result(false, s"setting ${setting.name} is not registered on this component")
    }
    else {
      val value = parseValue(setting, manager.getSetting(setting), args, valueIndex)
      manager.putSetting(setting, value)
      target match {
        case part: AEBasePart => part.saveChanges()
        case tile: AEBaseTile => tile.saveChanges()
        case _ =>
      }
      result(true)
    }
  }

  private def parseValue(setting: Settings, current: Enum[_], args: Arguments, index: Int): Enum[_] = {
    current match {
      case _: YesNo =>
        if (args.checkBoolean(index)) YesNo.YES else YesNo.NO
      case _ =>
        val name = args.checkString(index).trim.toUpperCase(Locale.ENGLISH)
        possibleValues(setting).find(_.name == name).getOrElse(throw new IllegalArgumentException(
          s"bad arguments #${index + 1} (excepted one of ${listValues(setting)})"))
    }
  }
}

object AESettingsEnvironment {
  trait AccessSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the access mode. Returns "READ_WRITE", "READ" or "WRITE".""")
    def getAccess(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.ACCESS, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the access mode. Accepts "READ_WRITE", "READ" or "WRITE".""")
    def setAccess(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.ACCESS, context, args)
  }

  trait ExtractionModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the extraction mode. Returns "STRICT" or "LOOSE".""")
    def getExtractionMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.EXTRACTION_MODE, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the extraction mode. Accepts "STRICT" or "LOOSE".""")
    def setExtractionMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.EXTRACTION_MODE, context, args)
  }

  trait FuzzyModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the fuzzy mode. Returns "IGNORE_ALL", "PERCENT_99", "PERCENT_75", "PERCENT_50", "PERCENT_25", "PERCENT_10" or "PERCENT_1".""")
    def getFuzzyMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.FUZZY_MODE, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the fuzzy mode. Accepts "IGNORE_ALL", "PERCENT_99", "PERCENT_75", "PERCENT_50", "PERCENT_25", "PERCENT_10" or "PERCENT_1".""")
    def setFuzzyMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.FUZZY_MODE, context, args)
  }

  trait StorageFilterSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the storage filter. Returns "NONE" or "EXTRACTABLE_ONLY".""")
    def getStorageFilter(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.STORAGE_FILTER, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the storage filter. Accepts "NONE" or "EXTRACTABLE_ONLY".""")
    def setStorageFilter(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.STORAGE_FILTER, context, args)
  }

  trait StickyModeSetting extends AESettingsEnvironment {
    @Callback(doc = "function(side:number):boolean -- Get whether sticky mode is enabled.")
    def getStickyMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.STICKY_MODE, context, args)

    @Callback(doc = "function(side:number, value:boolean):boolean -- Set whether sticky mode is enabled.")
    def setStickyMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.STICKY_MODE, context, args)
  }

  trait RedstoneControlledSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the redstone mode. Returns "IGNORE", "LOW_SIGNAL" or "HIGH_SIGNAL".""")
    def getRedstoneControlled(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.REDSTONE_CONTROLLED, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the redstone mode. Accepts "IGNORE", "LOW_SIGNAL" or "HIGH_SIGNAL".""")
    def setRedstoneControlled(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.REDSTONE_CONTROLLED, context, args)
  }

  trait CraftOnlySetting extends AESettingsEnvironment {
    @Callback(doc = "function(side:number):boolean -- Get whether only craftable items are exported.")
    def getCraftOnly(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.CRAFT_ONLY, context, args)

    @Callback(doc = "function(side:number, value:boolean):boolean -- Set whether only craftable items are exported.")
    def setCraftOnly(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.CRAFT_ONLY, context, args)
  }

  trait SchedulingModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function(side:number):string -- Get the scheduling mode. Returns "DEFAULT" or "ROUNDROBIN".""")
    def getSchedulingMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.SCHEDULING_MODE, context, args)

    @Callback(doc = """function(side:number, value:string):boolean -- Set the scheduling mode. Accepts "DEFAULT" or "ROUNDROBIN".""")
    def setSchedulingMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.SCHEDULING_MODE, context, args)
  }

  trait BlockingModeSetting extends AESettingsEnvironment {
    @Callback(doc = "function([side:number]):boolean -- Get whether blocking mode is enabled.")
    def getBlockingMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.BLOCK, context, args)

    @Callback(doc = "function([side:number], value:boolean):boolean -- Set whether blocking mode is enabled.")
    def setBlockingMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.BLOCK, context, args)
  }

  trait SmartBlockSetting extends AESettingsEnvironment {
    @Callback(doc = "function([side:number]):boolean -- Get whether smart blocking is enabled.")
    def getSmartBlock(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.SMART_BLOCK, context, args)

    @Callback(doc = "function([side:number], value:boolean):boolean -- Set whether smart blocking is enabled.")
    def setSmartBlock(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.SMART_BLOCK, context, args)
  }

  trait InterfaceTerminalSetting extends AESettingsEnvironment {
    @Callback(doc = "function([side:number]):boolean -- Get whether it is visible in interface terminals.")
    def getInterfaceTerminal(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.INTERFACE_TERMINAL, context, args)

    @Callback(doc = "function([side:number], value:boolean):boolean -- Set whether it is visible in interface terminals.")
    def setInterfaceTerminal(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.INTERFACE_TERMINAL, context, args)
  }

  trait InsertionModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function([side:number]):string -- Get the insertion mode. Returns "DEFAULT", "PREFER_EMPTY" or "ONLY_EMPTY".""")
    def getInsertionMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.INSERTION_MODE, context, args)

    @Callback(doc = """function([side:number], value:string):boolean -- Set the insertion mode. Accepts "DEFAULT", "PREFER_EMPTY" or "ONLY_EMPTY".""")
    def setInsertionMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.INSERTION_MODE, context, args)
  }

  trait AdvancedBlockingModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function([side:number]):string -- Get the advanced blocking mode. Returns "DEFAULT" or "BLOCK_ON_ALL".""")
    def getAdvancedBlockingMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.ADVANCED_BLOCKING_MODE, context, args)

    @Callback(doc = """function([side:number], value:string):boolean -- Set the advanced blocking mode. Accepts "DEFAULT" or "BLOCK_ON_ALL".""")
    def setAdvancedBlockingMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.ADVANCED_BLOCKING_MODE, context, args)
  }

  trait LockCraftingModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function([side:number]):string -- Get the crafting lock mode. Returns "NONE", "LOCK_UNTIL_PULSE", "LOCK_WHILE_HIGH" or "LOCK_WHILE_LOW".""")
    def getLockCraftingMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.LOCK_CRAFTING_MODE, context, args)

    @Callback(doc = """function([side:number], value:string):boolean -- Set the crafting lock mode. Accepts "NONE", "LOCK_UNTIL_PULSE", "LOCK_WHILE_HIGH" or "LOCK_WHILE_LOW".""")
    def setLockCraftingMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.LOCK_CRAFTING_MODE, context, args)
  }

  trait PatternOptimizationSetting extends AESettingsEnvironment {
    @Callback(doc = "function([side:number]):boolean -- Get whether pattern optimization is enabled.")
    def getPatternOptimization(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.PATTERN_OPTIMIZATION, context, args)

    @Callback(doc = "function([side:number], value:boolean):boolean -- Set whether pattern optimization is enabled.")
    def setPatternOptimization(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.PATTERN_OPTIMIZATION, context, args)
  }

  trait SidelessModeSetting extends AESettingsEnvironment {
    @Callback(doc = """function():string -- Get the sideless mode. Returns "SIDED" or "SIDELESS".""")
    def getSidelessMode(context: Context, args: Arguments): Array[AnyRef] = readSetting(Settings.SIDELESS_MODE, context, args)

    @Callback(doc = """function(value:string):boolean -- Set the sideless mode. Accepts "SIDED" or "SIDELESS".""")
    def setSidelessMode(context: Context, args: Arguments): Array[AnyRef] = writeSetting(Settings.SIDELESS_MODE, context, args)
  }
}
