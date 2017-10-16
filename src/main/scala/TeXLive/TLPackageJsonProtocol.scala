package TeXLive

import spray.json._

import scala.collection.immutable

/*
  object TLPackageJsonProtocolNotWorkingWithUndefinedKeysIngetFields extends DefaultJsonProtocol {
  implicit object TLPackageFormat extends RootJsonFormat[TLPackage] {
    def write(c: TLPackage) = ???
    def read(v: JsValue) = {
      v.asJsObject.getFields("name","shortdesc","longdesc","revision","category","runfiles","srcfiles",
        "docfiledata","cataloguedata","depends","catalogue","relocated") match {
        case Seq(JsString(name),JsString(shortdesc),JsString(longdesc),JsNumber(revision),JsString(category),JsArray(runfiles),JsArray(srcfiles),
        JsObject(docfiledata),JsObject(cataloguedata),JsArray(depends),JsString(catalogue),JsBoolean(relocated)) =>
        {
          val docf = docfiledata.map {
            p =>
              val det = p._2.asJsObject.fields.getOrElse[JsValue]("details", JsString("")).toString
              val lan = p._2.asJsObject.fields.getOrElse[JsValue]("lang", JsString("")).toString
              new DocFile(p._1, det, lan)
          }.toList
          val catdat = CatalogueData(cataloguedata("version").toString, cataloguedata("topics").toString,
            cataloguedata("license").toString, cataloguedata("date").toString, cataloguedata("ctan").toString)
          new TLPackage(name, shortdesc, longdesc, revision.toLong, category, docf,
            runfiles.map(_.convertTo[String]).toList,srcfiles.map(_.convertTo[String]).toList,
            catdat, depends.map(_.convertTo[String]).toList,catalogue,relocated)

        }
        case _ => throw new DeserializationException("TLPackage expected: " + v.prettyPrint)
      }
    }
  }
}
*/

object TLPackageJsonProtocol extends DefaultJsonProtocol {
  implicit object TLPackageFormat extends RootJsonFormat[TLPackage] {
    def convit[S <: JsValue](v: Map[String, JsValue], n: String, d: S) = {
      val foo = v.getOrElse[JsValue](n,d)
      if (foo == JsNull) {
        d
      } else {
        foo
      }
    }
    def write(c: TLPackage) = ???
    def read(v: JsValue) = {
      val vmap: Map[String, JsValue] = v.asJsObject.fields
      val name      = convit[JsString](vmap,"name", JsString("")).convertTo[String]
      // val name      = vmap.getOrElse[JsValue]("name", JsString("")).convertTo[String]
      val shortdesc = convit[JsString](vmap,"shortdesc", JsString("")).convertTo[String]
      val longdesc  = convit[JsString](vmap,"longdesc", JsString("")).convertTo[String]
      val category  = convit[JsString](vmap,"category", JsString("")).convertTo[String]
      val catalogue = convit[JsString](vmap,"catalogue", JsString("")).convertTo[String]
      val lrev      = convit[JsNumber](vmap,"lrev",JsNumber(0)).convertTo[Long]
      val rrev      = convit[JsNumber](vmap,"rrev",JsNumber(0)).convertTo[Long]
      val srcsize   = convit[JsNumber](vmap,"srcsize",JsNumber(0)).convertTo[Long] * 4096
      val docsize   = convit[JsNumber](vmap,"docsize",JsNumber(0)).convertTo[Long] * 4096
      val runsize   = convit[JsNumber](vmap,"runsize",JsNumber(0)).convertTo[Long] * 4096
      val srcfiles  = convit[JsValue](vmap,"srcfiles",JsArray()).convertTo[List[String]]
      val runfiles  = convit[JsValue](vmap,"runfiles",JsArray()).convertTo[List[String]]
      val docfiles  = convit[JsValue](vmap,"docfiles",JsArray()).convertTo[List[String]]
      val depends   = convit[JsValue](vmap,"depends",JsArray()).convertTo[List[String]]
      val executes  = convit[JsValue](vmap,"executes",JsArray()).convertTo[List[String]]
      val relocated = convit[JsValue](vmap,"relocated", JsBoolean(false)).convertTo[Boolean]
      val installed = convit[JsValue](vmap,"installed", JsBoolean(false)).convertTo[Boolean]
      val available = convit[JsValue](vmap,"available", JsBoolean(false)).convertTo[Boolean]
      val catdata   = convit[JsValue](vmap,"cataloguedata", JsObject()).convertTo[JsObject]
      val binfilesd = convit[JsValue](vmap,"binfiles", JsObject()).convertTo[JsObject]
      val binsizesd = convit[JsValue](vmap,"binsize", JsObject()).convertTo[JsObject]
      val dfd = v.asJsObject.getFields("docfiledata") match {
        case Seq(JsObject(docfiledata)) =>
          docfiledata.map {
            p =>
              val det = p._2.asJsObject.fields.getOrElse[JsValue]("details", JsString("")).toString
              val lan = p._2.asJsObject.fields.getOrElse[JsValue]("lang", JsString("")).toString
              (p._1, (det,lan))
          }.toMap[String,(String,String)]
        case Seq() => Map[String,(String,String)]()
        case _ => throw new DeserializationException("Strange docfiledata")
      }
      val dfds = docfiles.map(f => {
        val p = dfd.getOrElse("f",("",""))
        new DocFile(f,p._1,p._2)
      })
      // further parse of binfiles
      val binfiles = binfilesd.fields.flatMap { _._2.convertTo[List[String]] }.toList
      val binsize = binsizesd.fields.map { _._2.convertTo[Long]}.toList.foldLeft[Long](0)(_ + _) * 4096

      // further parse the catdata
      val catversion = catdata.fields.getOrElse[JsValue]("version", JsString("")).convertTo[String]
      val cattopics  = catdata.fields.getOrElse[JsValue]("topics", JsString("")).convertTo[String]
      val catlicense = catdata.fields.getOrElse[JsValue]("license", JsString("")).convertTo[String]
      val catdate    = catdata.fields.getOrElse[JsValue]("date", JsString("")).convertTo[String]
      val catctan    = catdata.fields.getOrElse[JsValue]("ctan", JsString("")).convertTo[String]
      val catrelated = catdata.fields.getOrElse[JsValue]("related", JsString("")).convertTo[String]
      val catdat     = new CatalogueData(catversion,cattopics,catlicense,catdate,catrelated,catctan)
      new TLPackage(name, shortdesc, longdesc, lrev, rrev, category, dfds, runfiles, srcfiles, binfiles, docsize, runsize, srcsize, binsize, catdat, depends, catalogue, relocated, installed, available)
    }
  }
}