import li.cil.oc.client.renderer.tileentity.HologramFaces
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HologramFacesTest extends FunSpec {
  describe("Hologram face packing") {
    it("round-trips every field at its extents") {
      for (x <- Seq(0, 47); y <- Seq(0, 31); z <- Seq(0, 47); face <- 0 until HologramFaces.FaceCount; value <- 1 to 3) {
        val packed = HologramFaces.pack(x, y, z, face, value)
        (HologramFaces.x(packed), HologramFaces.y(packed), HologramFaces.z(packed), HologramFaces.face(packed), HologramFaces.value(packed)) should be((x, y, z, face, value))
      }
    }
  }
}
