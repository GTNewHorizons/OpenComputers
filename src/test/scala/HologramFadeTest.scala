import li.cil.oc.client.renderer.tileentity.HologramFade
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HologramFadeTest extends FunSpec {
  describe("Hologram fade") {
    it("stays at 0.75 without NaN when the fade start is not below the maximum") {
      HologramFade.alpha(100, 4096, 4096) should be(0.75f)
      HologramFade.alpha(5000, 4096, 4096) should be(0.75f)
      HologramFade.alpha(5000, 6000, 4096) should be(0.75f)
      HologramFade.alpha(0, 0, 0) should be(0.75f)
    }
  }
}
