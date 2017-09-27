package TeXLive

import scalafx.beans.property.{BooleanProperty, IntegerProperty, ObjectProperty, StringProperty}

/*
class TLPackage(name_ : String, lrev_ : String, rrev_ : String, shortdesc_ : String, size_ : String, installed_ : String) {
  val name = new StringProperty(this, "name", name_)
  val shortdesc = new StringProperty(this, "shortdesc", shortdesc_)
  val lrev = new StringProperty(this, "revision", lrev_)
  val rrev = new StringProperty(this, "revision", rrev_)
  val size = new StringProperty(this, "size", size_)
  val installed = new StringProperty(this, "installed", if (installed_ == "1") "Installed" else "Not installed")
}
*/

// note!!! we have ot use ObjectProperty[Int] here instead of IntegerProperty
// since IntegerProperty does NOT implement Observable[Int,Int]
// see https://github.com/scalafx/scalafx/issues/243
case class TLPackage(name: StringProperty, lrev: ObjectProperty[Int], rrev: ObjectProperty[Int], shortdesc: StringProperty, size: ObjectProperty[Int], installed: StringProperty) {
  def this(_name: String, _lrev: String, _rrev: String, _shortdesc: String, _size: String, _installed: String) =
    this(
      StringProperty(_name), ObjectProperty[Int](_lrev.toInt), ObjectProperty[Int](_rrev.toInt), StringProperty(_shortdesc),
      ObjectProperty[Int](_size.toInt), StringProperty(_installed))
}