package TeXLive

import scalafx.beans.property.StringProperty
import scalafx.beans.property.IntegerProperty
import scalafx.beans.property.BooleanProperty

class TLPackage(name_ : String, lrev_ : String, rrev_ : String, shortdesc_ : String, size_ : String, installed_ : String) {
  val name = new StringProperty(this, "name", name_)
  val shortdesc = new StringProperty(this, "shortdesc", shortdesc_)
  val lrev = new StringProperty(this, "revision", lrev_)
  val rrev = new StringProperty(this, "revision", rrev_)
  val size = new StringProperty(this, "size", size_)
  val installed = new StringProperty(this, "installed", installed_)
}
