package li.cil.oc.client.renderer.text

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap

trait GlyphStore {
  def store(texture: Int, char: Int, x: Int, y: Int): Boolean

  def clear(texture: Int, x: Int, y: Int, width: Int): Unit
}

object GlyphAtlas {
  final val Width = 2048
  final val GlyphSize = 16
  final val Columns = Width / GlyphSize
  final val InitialHeight = 256
  final val MaxSlots = 1 << 15
  final val ResetCooldown = 100
}

final class GlyphAtlas(textures: SheetTextures, store: GlyphStore, maxHeight: Int) extends GlyphSlots {
  import GlyphAtlas._

  private final val Unmapped = -1

  private val slots = {
    val map = new Int2IntOpenHashMap(256, 0.5f)
    map.defaultReturnValue(Unmapped)
    map
  }

  private val heightLimit = math.min(maxHeight, MaxSlots / Columns * GlyphSize)

  var height: Int = math.min(InitialHeight, heightLimit)

  var texture: Int = textures.create(Width, height)

  var generation = 0

  private var count = 1

  private var resetPending = false

  private var cooldown = 0

  store.clear(texture, 0, 0, GlyphSize)
  slotOf('?')

  override def slotOf(char: Int): Int = {
    if (char == ' ') return 0
    var slot = slots.get(char)
    if (slot != Unmapped) return slot
    if (count == height / GlyphSize * Columns && !grow()) {
      resetPending = true
      slot = slots.get('?')
      return if (slot == Unmapped) 0 else slot
    }
    if (store.store(texture, char, (count % Columns) * GlyphSize, (count / Columns) * GlyphSize)) {
      slot = count
      count += 1
    }
    else slot = if (char == '?') 0 else slotOf('?')
    slots.put(char, slot)
    slot
  }

  def beginFrame() {
    if (cooldown > 0) cooldown -= 1
    else if (resetPending) {
      reset()
      cooldown = ResetCooldown
    }
  }

  def reset() {
    slots.clear()
    count = 1
    generation += 1
    resetPending = false
    slotOf('?')
  }

  def dispose(): Unit = textures.delete(texture)

  private def grow(): Boolean = {
    if (height >= heightLimit) return false
    val next = math.min(height * 2, heightLimit)
    val grown = textures.create(Width, next)
    textures.copy(texture, grown, Width, height)
    textures.delete(texture)
    texture = grown
    height = next
    true
  }
}
