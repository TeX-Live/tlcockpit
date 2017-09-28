package TeXLive

import scalafx.beans.property.{ObjectProperty, StringProperty}

// Note!!! we have to use ObjectProperty[Int] here instead of IntegerProperty
// since IntegerProperty does NOT implement Observable[Int,Int]
// see https://github.com/scalafx/scalafx/issues/243
case class TLPackage(name: StringProperty, lrev: ObjectProperty[Int], rrev: ObjectProperty[Int],
                     shortdesc: StringProperty, size: ObjectProperty[Int], installed: StringProperty) {
  def this(_name: String, _lrev: String, _rrev: String, _shortdesc: String, _size: String, _installed: String) =
    this(
      StringProperty(_name),      ObjectProperty[Int](_lrev.toInt), ObjectProperty[Int](_rrev.toInt),
      StringProperty(_shortdesc), ObjectProperty[Int](_size.toInt), StringProperty(_installed)
    )
}