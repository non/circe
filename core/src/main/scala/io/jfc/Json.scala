package io.jfc

import algebra.Eq
import cats.Show
import cats.data.Xor
import cats.std.list._

/**
 * A data type representing possible JSON values.
 *
 * @author Travis Brown
 * @author Tony Morris
 * @author Dylan Just
 * @author Mark Hibberd
 */
sealed abstract class Json extends Product with Serializable {
  import Json._
  /**
   * The catamorphism for the JSON value data type.
   */
  def fold[X](
    jsonNull: => X,
    jsonBool: Boolean => X,
    jsonNumber: JsonNumber => X,
    jsonString: String => X,
    jsonArray: List[Json] => X,
    jsonObject: JsonObject => X
  ): X = this match {
    case JNull      => jsonNull
    case JBool(b)   => jsonBool(b)
    case JNumber(n) => jsonNumber(n)
    case JString(s) => jsonString(s)
    case JArray(a)  => jsonArray(a.toList)
    case JObject(o) => jsonObject(o)
  }

  /**
   * Run on an array or object or return the given default.
   */
  def arrayOrObject[X](
    or: => X,
    jsonArray: List[Json] => X,
    jsonObject: JsonObject => X
  ): X = this match {
    case JNull      => or
    case JBool(_)   => or
    case JNumber(_) => or
    case JString(_) => or
    case JArray(a)  => jsonArray(a.toList)
    case JObject(o) => jsonObject(o)
  }

  /**
   * Construct a cursor from this JSON value.
   */
  def cursor: Cursor = Cursor(this)

  /**
   * Construct a cursor with history from this JSON value.
   */
  def hcursor: HCursor = Cursor(this).hcursor

  def isNull: Boolean = false
  def isBoolean: Boolean = false
  def isNumber: Boolean = false
  def isString: Boolean = false
  def isArray: Boolean = false
  def isObject: Boolean = false

  def asBoolean: Option[Boolean] = None
  def asNumber: Option[JsonNumber] = None
  def asString: Option[String] = None
  def asArray: Option[List[Json]] = None
  def asObject: Option[JsonObject] = None

  def withBoolean(f: Boolean => Json): Json = asBoolean.fold(this)(f)
  def withNumber(f: JsonNumber => Json): Json = asNumber.fold(this)(f)
  def withString(f: String => Json): Json = asString.fold(this)(f)
  def withArray(f: List[Json] => Json): Json = asArray.fold(this)(f)
  def withObject(f: JsonObject => Json): Json = asObject.fold(this)(f)

  def mapBoolean(f: Boolean => Boolean): Json = this
  def mapNumber(f: JsonNumber => JsonNumber): Json = this
  def mapString(f: String => String): Json = this
  def mapArray(f: List[Json] => List[Json]): Json = this
  def mapObject(f: JsonObject => JsonObject): Json = this

  /**
   * The name of the type of the JSON value.
   */
  def name: String =
    this match {
      case JNull      => "Null"
      case JBool(_)   => "Boolean"
      case JNumber(_) => "Number"
      case JString(_) => "String"
      case JArray(_)  => "Array"
      case JObject(_) => "Object"
    }

  /**
   * Attempts to decode this JSON value to another data type.
   */
  def as[A](implicit d: Decode[A]): Xor[DecodeFailure, A] = d(cursor.hcursor)

  /**
   * Pretty-print this JSON value to a string using the given pretty-printing parameters.
   */
  def pretty(p: Printer): String = p.pretty(this)

  /**
   * Pretty-print this JSON value to a string with no spaces.
   */
  def noSpaces: String = Printer.noSpaces.pretty(this)

  /**
   * Pretty-print this JSON value to a string indentation of two spaces.
   */
  def spaces2: String = Printer.spaces2.pretty(this)

  /**
   * Pretty-print this JSON value to a string indentation of four spaces.
   */
  def spaces4: String = Printer.spaces4.pretty(this)

  /**
   * Compute a `String` representation for this JSON value.
   */
  override def toString: String = spaces2
}

object Json {
  private[jfc] case object JNull extends Json {
    override def isNull: Boolean = true
  }
  private[jfc] final case class JBool(b: Boolean) extends Json {
    override def isBoolean: Boolean = true
    override def asBoolean: Option[Boolean] = Some(b)
    override def mapBoolean(f: Boolean => Boolean): Json = JBool(f(b))
  }
  private[jfc] final case class JNumber(n: JsonNumber) extends Json {
    override def isNumber: Boolean = true
    override def asNumber: Option[JsonNumber] = Some(n)
    override def mapNumber(f: JsonNumber => JsonNumber): Json = JNumber(f(n))
  }
  private[jfc] final case class JString(s: String) extends Json {
    override def isString: Boolean = true
    override def asString: Option[String] = Some(s)
    override def mapString(f: String => String): Json = JString(f(s))
  }
  private[jfc] final case class JArray(a: Seq[Json]) extends Json {
    override def isArray: Boolean = true
    override def asArray: Option[List[Json]] = Some(a.toList)
    override def mapArray(f: List[Json] => List[Json]): Json = JArray(f(a.toList))
  }
  private[jfc] final case class JObject(o: JsonObject) extends Json {
    override def isObject: Boolean = true
    override def asObject: Option[JsonObject] = Some(o)
    override def mapObject(f: JsonObject => JsonObject): Json = JObject(f(o))
  }

  val empty: Json = JNull
  def bool(b: Boolean): Json = JBool(b)
  def int(n: Int): Json = JNumber(JsonLong(n.toLong))
  def long(n: Long): Json = JNumber(JsonLong(n))
  def number(n: Double): Option[Json] = JsonDouble(n).asJson
  def numberOrNull(n: Double): Json = JsonDouble(n).asJsonOrNull
  def numberOrString(n: Double): Json = JsonDouble(n).asJsonOrString
  def string(s: String): Json = JString(s)
  def array(elements: Json*): Json = JArray(elements)
  def obj(fields: (String, Json)*): Json = JObject(JsonObject.from(fields.toList))

  def fromJsonNumber(num: JsonNumber): Json = JNumber(num)
  def fromJsonObject(obj: JsonObject): Json = JObject(obj)
  def fromFields(fields: Seq[(String, Json)]): Json = JObject(JsonObject.from(fields.toList))
  def fromValues(values: Seq[Json]): Json = JArray(values)

  implicit val eqJson: Eq[Json] = Eq.instance {
    case (JObject(a), JObject(b)) => Eq[JsonObject].eqv(a, b)
    case (JString(a), JString(b)) => a == b
    case (JNumber(a), JNumber(b)) => Eq[JsonNumber].eqv(a, b)
    case (  JBool(a),   JBool(b)) => a == b
    case ( JArray(a),  JArray(b)) => Eq[List[Json]].eqv(a.toList, b.toList)
    case (     JNull,      JNull) => true
    case (         _,          _) => false
  }

  implicit val showJson: Show[Json] = Show.fromToString[Json]
}
