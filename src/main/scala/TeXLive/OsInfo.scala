package TeXLive

object OsInfo {
  val OS = System.getProperty("os.name")
  def isWindows: Boolean = {
    OS.startsWith("Windows")
  }
}
