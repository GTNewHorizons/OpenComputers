package li.cil.oc.client.renderer.tileentity

object OverlayState {
  final val NoMesh = -1

  final val ComputerRunning = 1
  final val ComputerActivity = 2
  final val ComputerError = 4
  final val ComputerStates = 8

  def computer(running: Boolean, activity: Boolean, errorLight: Boolean): Int =
    if (running) {
      if (activity) ComputerRunning | ComputerActivity else ComputerRunning
    }
    else if (errorLight) ComputerError
    else NoMesh

  final val MicrocontrollerRunning = 1
  final val MicrocontrollerError = 2
  final val MicrocontrollerStates = 3

  def microcontroller(running: Boolean, errorLight: Boolean): Int =
    if (running) MicrocontrollerRunning
    else if (errorLight) MicrocontrollerError
    else 0

  final val RaidSlotBits = 3
  final val RaidStates = 1 << 5

  def raid(missingSlots: Int, activeSlot: Int): Int = {
    val active = if (activeSlot >= 0 && activeSlot < RaidSlotBits) activeSlot + 1 else 0
    val packed = (missingSlots & ((1 << RaidSlotBits) - 1)) | active << RaidSlotBits
    if (packed == 0) NoMesh else packed
  }

  def raidState(presence: Array[Boolean], slots: Int, lastAccess: Long, active: Boolean): Int = {
    var missing = 0
    var slot = 0
    while (slot < slots) {
      if (!presence(slot)) missing |= 1 << slot
      slot += 1
    }
    val activeSlot = if (active) (lastAccess % slots).toInt else -1
    raid(missing, activeSlot)
  }

  def raidMissingSlots(state: Int): Int = state & ((1 << RaidSlotBits) - 1)

  def raidActiveSlot(state: Int): Int = (state >> RaidSlotBits) - 1

  def raidActivityVisible(state: Int): Boolean = {
    val active = raidActiveSlot(state)
    active >= 0 && (raidMissingSlots(state) & (1 << active)) == 0
  }

  final val ChargerLevels = 15
  final val ChargerStates = 1 << 5

  private final val ChargerLevelMask = 15
  private final val ChargerPowered = 16

  def charger(chargeSpeed: Double, powered: Boolean): Int =
    if (chargeSpeed <= 0) NoMesh
    else {
      val level = math.max(1, math.min(math.round(chargeSpeed * ChargerLevels).toInt, ChargerLevels))
      if (powered) level | ChargerPowered else level
    }

  def chargerSpeed(state: Int): Double = (state & ChargerLevelMask) / ChargerLevels.toDouble

  def chargerPowered(state: Int): Boolean = (state & ChargerPowered) != 0
}
