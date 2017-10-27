package TeXLive


case class CatalogueData(version: Option[String], topics: Option[String], license: Option[String],
                         date: Option[String], related: Option[String], ctan: Option[String])

case class DocFile(file: String, details: Option[String], language: Option[String])

case class TLPackage
(
  name: String,
  shortdesc: Option[String],
  longdesc: Option[String],
  lrev: Long,
  rrev: Long,
  category: String,
  docfiles: List[DocFile],
  runfiles: List[String],
  srcfiles: List[String],
  binfiles: Map[String,List[String]],
  docsize: Long,
  runsize: Long,
  srcsize: Long,
  binsize: Map[String,Long],
  cataloguedata: CatalogueData,
  depends: List[String],
  catalogue: Option[String],
  relocated: Boolean,
  installed: Boolean,
  available: Boolean
)

