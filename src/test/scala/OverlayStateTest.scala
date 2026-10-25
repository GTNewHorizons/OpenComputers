import li.cil.oc.client.renderer.tileentity.OverlayState
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OverlayStateTest extends FunSpec {
  describe("Overlay state encoders") {
    it("keeps every encoder inside its key table") {
      val charger = OverlayState.charger(100, powered = true)
      val cases =
        (for (running <- Seq(false, true); activity <- Seq(false, true); error <- Seq(false, true))
          yield (s"computer($running, $activity, $error)", OverlayState.computer(running, activity, error), OverlayState.ComputerStates)) ++
        (for (missing <- Seq(-1, 0, 5, 255); active <- Seq(-9, -1, 0, 2, 3, 100))
          yield (s"raid($missing, $active)", OverlayState.raid(missing, active), OverlayState.RaidStates)) ++
        Seq(("charger(100, true)", charger, OverlayState.ChargerStates)) ++
        (for (running <- Seq(false, true); error <- Seq(false, true))
          yield (s"microcontroller($running, $error)", OverlayState.microcontroller(running, error), OverlayState.MicrocontrollerStates))
      for ((name, state, bound) <- cases) {
        withClue(s"$name = $state, bound $bound: ") {
          (state == OverlayState.NoMesh || (state >= 0 && state < bound)) should be(true)
        }
      }
      OverlayState.chargerSpeed(charger) should be(1.0)
    }
  }

  describe("Raid overlay state") {
    it("round-trips every missing mask and active slot") {
      for (missing <- 0 until 8; active <- -1 until 3 if missing != 0 || active >= 0) {
        val state = OverlayState.raid(missing, active)
        (state >= 0 && state < OverlayState.RaidStates) should be(true)
        OverlayState.raidMissingSlots(state) should be(missing)
        OverlayState.raidActiveSlot(state) should be(active)
      }
    }
    it("suppresses the activity overlay when the active slot is missing") {
      val lastAccess = 9999L
      val missing = OverlayState.raidState(Array(true, false, true), 3, lastAccess, active = true)
      OverlayState.raidActiveSlot(missing) should be((lastAccess % 3).toInt)
      OverlayState.raidActivityVisible(missing) should be((lastAccess % 3) != 1)
      OverlayState.raidActivityVisible(OverlayState.raid(2, 1)) should be(false)
      OverlayState.raidActivityVisible(OverlayState.raid(2, 0)) should be(true)
      OverlayState.raidActivityVisible(OverlayState.raid(2, -1)) should be(false)
    }
  }

  describe("Charger overlay state") {
    it("represents each of the fifteen redstone levels exactly") {
      for (signal <- 1 to 15) {
        val state = OverlayState.charger(signal / 15.0, powered = false)
        OverlayState.chargerSpeed(state) should be(signal / 15.0)
      }
    }
  }
}
