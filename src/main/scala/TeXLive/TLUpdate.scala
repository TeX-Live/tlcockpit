package TeXLive

import scalafx.beans.property.{ObjectProperty, StringProperty}

// Note!!! we have to use ObjectProperty[Int] here instead of IntegerProperty
// since IntegerProperty does NOT implement Observable[Int,Int]
// see https://github.com/scalafx/scalafx/issues/243
case class TLUpdate(name: StringProperty, status: StringProperty, lrev: StringProperty, rrev: StringProperty,
                     shortdesc: StringProperty, size: StringProperty) {
  def this(_name: String, _status: String, _lrev: String, _rrev: String, _shortdesc: String, _size: String) =
    this(
      StringProperty(_name), StringProperty(_status), StringProperty(_lrev), StringProperty(_rrev),
      StringProperty(_shortdesc), StringProperty(_size)
    )
}