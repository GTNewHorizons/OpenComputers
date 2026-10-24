import li.cil.oc.client.renderer.tileentity.RobotLight
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RobotLightTest extends FunSpec {
  describe("Cycle step") {
    it("stays inside the step table and follows the fractional part") {
      for (offset <- Seq(0.0, 15.5, Int.MaxValue + 0.5, 9e15, -0.25, -7.9375, -1e-17)) {
        val step = RobotLight.step(offset)
        (step >= 0 && step < 16) should be(true)
      }
      RobotLight.step(Int.MaxValue + 0.5) should be(8)
    }
  }
}
