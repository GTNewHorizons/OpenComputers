package li.cil.oc.integration.util

object MapUtils {
  implicit class MapWrapper(val map: java.util.Map[_, _]) extends AnyVal {
    def getInt(key: String): Option[Int] = map.get(key) match {
      case value: java.lang.Number => Some(value.intValue)
      case _ => None
    }

    def getDouble(key: String): Option[Double] = map.get(key) match {
      case value: java.lang.Number => Some(value.doubleValue)
      case _ => None
    }

    def getFloat(key: String): Option[Float] = map.get(key) match {
      case value: java.lang.Number => Some(value.floatValue)
      case _ => None
    }

    def getLong(key: String): Option[Long] = map.get(key) match {
      case value: java.lang.Number => Some(value.longValue)
      case _ => None
    }

    def getBoolean(key: String): Option[Boolean] = map.get(key) match {
      case value: java.lang.Boolean => Some(value)
      case _ => None
    }

    def getString(key: String): Option[String] = map.get(key) match {
      case value: String => Some(value)
      case _ => None
    }

    def getMap(key: String): Option[java.util.Map[_,_]] = map.get(key) match {
      case value: java.util.Map[_,_] => Some(value)
      case _ => None
    }
  }
}
