package li.cil.oc.client.renderer.tileentity

object HologramFade {
  final val MaxAlpha = 0.75f

  def alpha(distSq: Double, fadeSq: Double, maxSq: Double): Float = {
    if (distSq <= fadeSq || maxSq <= fadeSq) MaxAlpha
    else MaxAlpha * math.max(0.0, 1 - (distSq - fadeSq) / (maxSq - fadeSq)).toFloat
  }
}
