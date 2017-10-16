package TeXLive

case class CatalogueData(version: String, topics: String, license:String,
                         date: String, related: String, ctan: String)

case class DocFile(file: String, details: String = "", language: String = "")

case class TLPackage
(
  name: String,
  shortdesc: String,
  longdesc: String,
  lrev: Long,
  rrev: Long,
  category: String,
  docfiles: List[DocFile],
  runfiles: List[String],
  srcfiles: List[String],
  binfiles: List[String],
  cataloguedata: CatalogueData,
  depends: List[String],
  catalogue: String,
  relocated: Boolean,
  installed: Boolean,
  available: Boolean
)