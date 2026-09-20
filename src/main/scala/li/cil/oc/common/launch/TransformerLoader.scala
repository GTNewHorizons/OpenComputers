package li.cil.oc.common.launch

import java.util

import cpw.mods.fml.relauncher.IFMLLoadingPlugin
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.MCVersion
import cpw.mods.fml.relauncher.IFMLLoadingPlugin.TransformerExclusions

@TransformerExclusions(Array("li.cil.oc.common.asm"))
@MCVersion("1.7.10")
class TransformerLoader extends IFMLLoadingPlugin {

  override def getModContainerClass = "li.cil.oc.common.launch.CoreModContainer"

  override def getASMTransformerClass = Array("li.cil.oc.common.asm.ClassTransformer")

  override def getAccessTransformerClass = null

  override def getSetupClass = null

  override def injectData(data: util.Map[String, AnyRef]) {}
}
