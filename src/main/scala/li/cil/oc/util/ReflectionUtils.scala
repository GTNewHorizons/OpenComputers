package li.cil.oc.util

import java.lang.invoke.{MethodHandle, MethodHandles}
import java.lang.reflect.{Field, Method}
import scala.collection.mutable

object ReflectionUtils {
  private case class MethodCacheKey(clazz: Class[_], name: String, allowSuper: Boolean, args: Class[_]*)
  private case class FieldCacheKey(clazz: Class[_], name: String, allowSuper: Boolean)
  private val methodCache: mutable.HashMap[MethodCacheKey, MethodHandle] = mutable.HashMap.empty;
  private val getterCache: mutable.HashMap[FieldCacheKey, MethodHandle] = mutable.HashMap.empty;
  private val setterCache: mutable.HashMap[FieldCacheKey, MethodHandle] = mutable.HashMap.empty;

  def getMethodHandle(clazz: Class[_], name: String, allowSuper: Boolean, args: Class[_]*): MethodHandle = {
    val key = MethodCacheKey(clazz, name, allowSuper, args: _*)
    methodCache.getOrElseUpdate(key, {
      var c: Class[_] = clazz
      var method: Method = null
      while (c != null  && method == null) {
        try {
          method = c.getDeclaredMethod(name, args: _*)
        } catch {
          case _: NoSuchMethodException =>
        }
        if (allowSuper) c = c.getSuperclass
        else c = null
      }
      if (method == null) {
        throw new RuntimeException(s"Method '$name' with params ${args.mkString("(", ", ", ")")} not found in $clazz" + (if (allowSuper) " or its superclasses" else ""))
      }
      try {
        method.setAccessible(true)
        MethodHandles.lookup.unreflect(method)
      } catch {
        case e: IllegalAccessException => throw new RuntimeException(s"Failed to unreflect method '$name' in $clazz", e)
      }
    })
  }

  def getFieldGetterHandle(clazz: Class[_], name: String, allowSuper: Boolean): MethodHandle =
  {
    val key = FieldCacheKey(clazz, name, allowSuper)
    getterCache.getOrElseUpdate(key, {
      var c: Class[_] = clazz
      var field: Field = null
      while (c != null  && field == null) {
        try {
          field = c.getDeclaredField(name)
        } catch {
          case _: NoSuchFieldException =>
        }
        if (allowSuper) c = c.getSuperclass
        else c = null
      }
      if (field == null) {
        throw new RuntimeException(s"Field '$name' not found in $clazz" + (if (allowSuper) " or its superclasses" else ""))
      }
      try {
        field.setAccessible(true)
        MethodHandles.lookup.unreflectGetter(field)
      } catch {
        case e: IllegalAccessException => throw new RuntimeException(s"Failed to unreflect field '$name' in $clazz", e)
      }
    })
  }

  def getFieldSetterHandle(clazz: Class[_], name: String, allowSuper: Boolean): MethodHandle =
  {
    val key = FieldCacheKey(clazz, name, allowSuper)
    setterCache.getOrElseUpdate(key, {
      var c: Class[_] = clazz
      var field: Field = null
      while (c != null  && field == null) {
        try {
          field = c.getDeclaredField(name)
        } catch {
          case _: NoSuchFieldException =>
        }
        if (allowSuper) c = c.getSuperclass
        else c = null
      }
      if (field == null) {
        throw new RuntimeException(s"Field '$name' not found in $clazz" + (if (allowSuper) " or its superclasses" else ""))
      }
      try {
        field.setAccessible(true)
        MethodHandles.lookup.unreflectSetter(field)
      } catch {
        case e: IllegalAccessException => throw new RuntimeException(s"Failed to unreflect field '$name' in $clazz", e)
      }
    })
  }
}
