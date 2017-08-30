package TeXLive

import scalafx.beans.property.StringProperty

class TLPackage(name_ : String, lrev_ : String, rrev_ : String, shortdesc_ : String) {
  val name = new StringProperty(this, "name", name_)
  val shortdesc = new StringProperty(this, "shortdesc", shortdesc_)
  val lrev = new StringProperty(this, "revision", lrev_)
  val rrev = new StringProperty(this, "revision", rrev_)
}
