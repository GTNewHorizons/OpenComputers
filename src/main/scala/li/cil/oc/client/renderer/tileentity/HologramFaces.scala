package li.cil.oc.client.renderer.tileentity

object HologramFaces {
  val faceNormals = Array(
    0, 0, 1,
    0, 0, -1,
    1, 0, 0,
    -1, 0, 0,
    0, 1, 0,
    0, -1, 0)

  val faceVertices = Array(
    1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0, 1,
    1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 1, 1, 1, 0, 0, 1,
    1, 1, 1, 1, 0, 1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0,
    0, 0, 1, 1, 0, 0, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
    1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 1,
    1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1)

  final val FaceCount = 6

  final val FaceStride = 4 * 5

  def pack(x: Int, y: Int, z: Int, face: Int, value: Int): Int = x | y << 6 | z << 12 | face << 18 | value << 21

  def x(packed: Int): Int = packed & 63

  def y(packed: Int): Int = (packed >> 6) & 63

  def z(packed: Int): Int = (packed >> 12) & 63

  def face(packed: Int): Int = (packed >> 18) & 7

  def value(packed: Int): Int = packed >>> 21
}
