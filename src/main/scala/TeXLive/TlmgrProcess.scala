package TeXLive

import java.io._

import TeXLive.OsTools._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.sys.process._

class TlmgrProcess(updout: String => Unit, upderr: String => Unit) {
  val inputString = new SyncVar[String]                 // used for the tlmgr process input
  // val outputString = new SyncVar[String]                // used for the tlmgr process output
  // val errorBuffer: StringBuffer = new StringBuffer()    // buffer used for both tmgr process error console AND logging

  val tlroot = "kpsewhich -var-value SELFAUTOPARENT".!!.trim
  // println("tlroot ==" + tlroot + "==")

  // set in only one place, in the main thread
  var process: Process = _

  def send_command(input: String): Unit = {
    try {
      assert(!inputString.isSet)
      inputString.put(input)
    } catch {
      case exc: Throwable =>
        upderr("Main thread: " +
          (if (exc.isInstanceOf[NoSuchElementException]) "Timeout" else "Exception: " + exc))
    }
  }

  def start_process(): Unit = {
    // process creation
    if (process == null) {
      val procIO = new ProcessIO(inputFn(_), outputFn(_, updout), outputFn(_, upderr))
      val processBuilder: ProcessBuilder = Seq({if (isWindows) "tlmgr.bat" else "tlmgr"}, "--machine-readable", "shell")
      process = processBuilder.run(procIO)
    }
  }

  def cleanup(): Unit = {
    if (process != null) {
      send_command("quit")
      process.destroy()
    }
  }

  /* The standard input passing function */
  private[this] def inputFn(stdin: OutputStream): Unit = {
    val writer = new BufferedWriter(new OutputStreamWriter(stdin))
    try {
      var input = ""
      while (true) {
        input = inputString.take()
        if (input == "quit") {
          stdin.close()
          return
        } else {
          writer.write(input + "\n")
          // println("writing " + input + " to process")
          writer.flush()
        }
      }
      stdin.close()
    } catch {
      case exc: Throwable =>
        stdin.close()
        // println("Exception in inputFn thread: " + exc + "\n")
        upderr("Input thread: Exception: " + exc + "\n")
    }
  }

  private[this] def outputFn(outStr: InputStream, updfun: String => Unit): Unit = {
    val reader = new BufferedReader(new InputStreamReader(outStr))
    try {
      var line: String = ""
      while (true) {
        line = reader.readLine
        // println("DDD did read " + line + " from process")
        try {
          updfun(line)
        } catch {
          case exc: Throwable =>
            upderr("Update output line function failed, continuing anyway (probably old tlmgr)!")
            // println("Update output line function failed, continuing anyway (probably old tlmgr)!")
        }
      }
      outStr.close()
    } catch {
      case exc: Throwable =>
        outStr.close()
        upderr("Output thread: Exception: " + exc + "\n")
    }
  }
}
