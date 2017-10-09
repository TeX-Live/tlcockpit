package TeXLive

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import sys.process._

object OsTools {
  val OS = System.getProperty("os.name").map(_.toLower)
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
      Seq("cmd", "start", absf)
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
