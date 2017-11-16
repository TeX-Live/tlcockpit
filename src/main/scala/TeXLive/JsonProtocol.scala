package TeXLive

import spray.json._


object JsonProtocol extends DefaultJsonProtocol {
  implicit val catalogueDataFormat = jsonFormat6(CatalogueData)
  implicit val docfileFormat = jsonFormat3(DocFile)
  implicit val tlpackageFormat = jsonFormat19(TLPackage)
  implicit val tlbackupFormat = jsonFormat3(TLBackup)
  implicit val tlpaperconfFormat = jsonFormat3(TLPaperConf)
  implicit val tloptionsFormat = jsonFormat14(TLOptions)
  implicit val tloptionFormat = jsonFormat6(TLOption)
}

