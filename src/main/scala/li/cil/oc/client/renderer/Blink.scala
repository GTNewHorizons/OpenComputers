package li.cil.oc.client.renderer

import java.util.Random

object Blink {
  final val ActivityMillis = 400L

  def activity(lastAccess: Long, rand: Random): Boolean = System.currentTimeMillis() - lastAccess < ActivityMillis && rand.nextDouble() > 0.1

  def shouldShowErrorLight(hash: Int): Boolean = {
    val time = System.currentTimeMillis + hash
    val timeSlice = time / 500
    timeSlice % 2 == 0
  }
}
