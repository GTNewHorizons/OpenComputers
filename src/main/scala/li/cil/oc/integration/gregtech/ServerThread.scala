package li.cil.oc.integration.gregtech

import li.cil.oc.common.EventHandler

// For direct callbacks, this wraps a method to be called on the next server tick, so machine states
// do not change while the machine ticks
object ServerThread {
  def schedule(task: Runnable): Unit = EventHandler.scheduleServer(() => task.run())
}
