// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TeXLive

// classes that wrap up structures in TeX Live
// all of them have associated JSON parsers

case class CatalogueData(version: Option[String], topics: Option[String], license: Option[String],
                         date: Option[String], related: Option[String], ctan: Option[String])

case class DocFile(file: String, details: Option[String], language: Option[String])
case class TLPaperConf(program: String, file: String, options: List[String])
case class TLBackup(name: String, rev: String, date: String)

case class TLPackageShort
(
  name: String,
  shortdesc: Option[String],
  var lrev: Long,
  rrev: Long,
  category: String,
  depends: List[String],
  var installed: Boolean,
  available: Boolean
)

case class TLPackage
(
  name: String,
  shortdesc: Option[String],
  longdesc: Option[String],
  var lrev: Long,
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
  var installed: Boolean,
  available: Boolean
)

case class TLOption (name: String, description: String, format: String,
  tlmgrname: String, value: Option[String], default: String)

case class TLOptions
(
  sys_bin: String,
  sys_man: String,
  sys_info: String,
  backupdir: String,
  autobackup: Long,
  install_srcfiles: Boolean,
  install_docfiles: Boolean,
  create_formats: Boolean,
  post_code: Boolean,
  generate_updmap: Boolean,
  location: Map[String,String],
  desktop_integration: Option[Boolean],
  w32_multi_user: Option[Boolean],
  file_assocs: Option[Long]
)


