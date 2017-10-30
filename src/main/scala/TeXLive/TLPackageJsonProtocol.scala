package TeXLive

import spray.json._


object TLPackageJsonProtocol extends DefaultJsonProtocol {
  implicit val catalogueDataFormat = jsonFormat6(CatalogueData)
  implicit val docfileFormat = jsonFormat3(DocFile)
  implicit val tlpackageFormat = jsonFormat20(TLPackage)
}

