package TeXLive

import scalafx.beans.property.StringProperty


case class TLBackupDisplay(name: StringProperty, rev: StringProperty, date: StringProperty) {
  def this(_name: String, _rev: String, _date: String) =
    this(StringProperty(_name), StringProperty(_rev), StringProperty(_date))
}
