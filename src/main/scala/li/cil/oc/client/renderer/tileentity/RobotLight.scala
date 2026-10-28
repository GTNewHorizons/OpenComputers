package li.cil.oc.client.renderer.tileentity

object RobotLight {
  final val Steps = 16

  def step(offset: Double): Int = math.min(((offset - math.floor(offset)) * Steps).toInt, Steps - 1)
}
