package TeXLive
// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import sys.process._

object OsTools {
  val OS: String = System.getProperty("os.name").map(_.toLower)
  def isWindows: Boolean = {
    OS.startsWith("windows")
  }
  def isApple: Boolean = {
    OS.startsWith("mac os")
  }
  def isUnix: Boolean = {
    !(isWindows || isApple)
  }

  def openFileCmd(f: String): Seq[String] = {
    val absf = new java.io.File(f).getCanonicalPath
    if (isWindows) {
      Seq("cmd", "/c", "start", absf)
    } else if (isApple) {
      Seq("open", absf)
    } else {
      Seq("xdg-open", absf)
    }
  }

  def openFile(f: String): Unit = {
    val cmd = openFileCmd(f)
    val bar = Future {
      try {
        cmd.!
      } catch {
        case e: Exception => println("Cannot run command: " + cmd + " -- " + e + "\n")
      }
    }
  }
}
