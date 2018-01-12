// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

import scalafx.beans.property.{ObjectProperty, StringProperty}


// Note!!! we have to use ObjectProperty[Int] here instead of IntegerProperty
// since IntegerProperty does NOT implement Observable[Int,Int]
// see https://github.com/scalafx/scalafx/issues/243
case class TLPackageDisplay(name: StringProperty, var lrev: ObjectProperty[Int], rrev: ObjectProperty[Int],
                            shortdesc: StringProperty, size: ObjectProperty[Int], var installed: StringProperty) {
  def this(_name: String, _lrev: String, _rrev: String, _shortdesc: String, _size: String, _installed: String) =
    this(
      StringProperty(_name),      ObjectProperty[Int](_lrev.toInt), ObjectProperty[Int](_rrev.toInt),
      StringProperty(_shortdesc), ObjectProperty[Int](_size.toInt), StringProperty(_installed)
    )
}

case class TLBackupDisplay(name: StringProperty, rev: StringProperty, date: StringProperty) {
  def this(_name: String, _rev: String, _date: String) =
    this(StringProperty(_name), StringProperty(_rev), StringProperty(_date))
}

case class TLUpdateDisplay(name: StringProperty, var status: StringProperty, var lrev: StringProperty, rrev: StringProperty,
                           shortdesc: StringProperty, size: StringProperty) {
  def this(_name: String, _status: String, _lrev: String, _rrev: String, _shortdesc: String, _size: String) =
    this(
      StringProperty(_name), StringProperty(_status), StringProperty(_lrev), StringProperty(_rrev),
      StringProperty(_shortdesc), StringProperty(_size)
    )
}