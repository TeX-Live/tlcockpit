// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

object Utils {
  /**
    * @see https://stackoverflow.com/questions/3263892/format-file-size-as-mb-gb-etc
    * @see https://en.wikipedia.org/wiki/Zettabyte
    * @param fileSize Up to Exabytes
    * @return
    */
  def humanReadableByteSize(fileSize: Long): String = {
    if(fileSize <= 0) return "0 B"
    // kilo, Mega, Giga, Tera, Peta, Exa, Zetta, Yotta
    val units: Array[String] = Array("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroup: Int = (Math.log10(fileSize)/Math.log10(1024)).toInt
    f"${fileSize/Math.pow(1024, digitGroup)}%3.1f ${units(digitGroup)}"
  }

  def toInt(s: String): Option[Int] = {
    try {
      Some(s.toInt)
    } catch {
      case e: Exception => None
    }
  }

}
