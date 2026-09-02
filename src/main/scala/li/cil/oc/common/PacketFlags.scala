package li.cil.oc.common

object PacketFlags {
  object DropFile {
    val Start = 1 << 0
    val Chunk = 1 << 1
    val End = 1 << 2
  }
}
