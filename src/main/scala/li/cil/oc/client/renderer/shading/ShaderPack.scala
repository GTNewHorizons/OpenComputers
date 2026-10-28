package li.cil.oc.client.renderer.shading

import java.lang.reflect.Method

object ShaderPack {
  private val api: AnyRef = try {
    Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null)
  }
  catch {
    case _: Throwable => null
  }

  private val inUseMethod: Method = if (api == null) null else api.getClass.getMethod("isShaderPackInUse")

  private val NoArgs = new Array[AnyRef](0)

  private var stale = true

  private var active = false

  def beginFrame() {
    stale = true
  }

  def inUse: Boolean = {
    if (stale) {
      stale = false
      active = inUseMethod != null && inUseMethod.invoke(api, NoArgs: _*).asInstanceOf[Boolean]
    }
    active
  }
}
