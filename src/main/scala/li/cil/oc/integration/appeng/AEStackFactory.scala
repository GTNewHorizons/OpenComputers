package li.cil.oc.integration.appeng

import appeng.api.storage.data.{IAEStack, IAEStackType}
import appeng.util.item.{AEFluidStack, AEItemStack}
import li.cil.oc.api.driver.Converter
import li.cil.oc.integration.vanilla.{ConverterFluidStack, ConverterItemStack}

import java.util
import scala.reflect.ClassTag

object AEStackFactory {
  case class AETypeEntry(stackType: IAEStackType[_ <: IAEStack[_ <: IAEStack[_ <: IAEStack[_]]]], converter: (IAEStack[_], util.Map[AnyRef, AnyRef]) => Unit, parser: util.Map[_, _] => IAEStack[_])

  private var idRegistry = Map[String, AETypeEntry]()
  private var classRegistry = Map[Class[_], AETypeEntry]()

  private class UnregisteredAETypeException(msg: String) extends RuntimeException(msg)

  def register[T <: IAEStack[T]](stackType: IAEStackType[T], converter: (IAEStack[_], util.Map[AnyRef, AnyRef]) => Unit, parser: util.Map[_, _] => T)(implicit tag: ClassTag[T]): Unit = {
    val entry = AETypeEntry(stackType, converter, parser)
    idRegistry += (stackType.getId -> entry)
    classRegistry += (tag.runtimeClass -> entry)
  }

  def parse[T <: IAEStack[T]](map: util.Map[_, _])(implicit tag: ClassTag[T]): T = {
    classRegistry.get(tag.runtimeClass) match {
      case Some(entry) => entry.parser(map).asInstanceOf[T]
      case None => throw new UnregisteredAETypeException(s"Type ${tag.runtimeClass} hasn't been registered");
    }
  }

  def parse(key: String, map: util.Map[_, _]): IAEStack[_] = {
    idRegistry.get(key) match {
      case Some(entry) => entry.parser(map)
      case None => throw new UnregisteredAETypeException(s"Type $key hasn't been registered");
    }
  }

  def convert(stack: IAEStack[_], map: util.Map[AnyRef, AnyRef]): Unit = {
    classRegistry.get(stack.getClass) match {
      case Some(entry) => entry.converter(stack, map)
      case None => throw new UnregisteredAETypeException(s"Type ${stack.getClass} hasn't been registered");
    }
  }

  def getEntry(key: String): Option[AETypeEntry] = idRegistry.get(key)

  def getEntry[T <: IAEStack[T]]()(implicit tag: ClassTag[T]): Option[AETypeEntry] = classRegistry.get(tag.runtimeClass)

  def getRegisteredTypes: Iterable[IAEStackType[_]] = idRegistry.values.map(_.stackType)
}

object ConverterAEItemStack extends Converter {
  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: AEItemStack => ConverterItemStack.convert(stack.getItemStack, output)
    case _ =>
  }

  def parse(args: util.Map[_, _]): AEItemStack = {
    AEItemStack.create(ConverterItemStack.parse(args))
  }
}

object ConverterAEFluidStack extends Converter {
  override def convert(value: Any, output: util.Map[AnyRef, AnyRef]): Unit = value match {
    case stack: AEFluidStack => ConverterFluidStack.convert(stack.getFluidStack, output)
    case _ =>
  }

  def parse(args: util.Map[_, _]): AEFluidStack = {
    AEFluidStack.create(ConverterFluidStack.parse(args))
  }
}
