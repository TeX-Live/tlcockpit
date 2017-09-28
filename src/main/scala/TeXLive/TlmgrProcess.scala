package TeXLive

import java.io._

import OsInfo._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.sys.process.{Process, ProcessBuilder, ProcessIO}

class TlmgrProcess(updout: Array[String] => Unit, upderr: String => Unit) {
  val inputString = new SyncVar[String]                 // used for the tlmgr process input
  val outputString = new SyncVar[String]                // used for the tlmgr process output
  val errorBuffer: StringBuffer = new StringBuffer()    // buffer used for both tmgr process error console AND logging

  // set in only one place, in the main thread
  var process: Process = _
  var lastInput: String = ""
  var lastOk: Boolean = false
  var isBusy = false


  def send_command(input: String): Array[String] = {

    lastInput = input

    val maxWaitTime = 5000
    var waited = 0
    while (isBusy) {
      // do busy waiting here in case some other tlmgr command is running
      // println("Debug: waiting for tlmgr being ready, want to send " + input)
      Thread.sleep(300)
      waited += 300
      if (waited > maxWaitTime) {
        throw new Exception("tlmgr busy, waited too long, aborting calling: " + input)
      }
    }
    try {
      // pass the input and wait for the output
      assert(!inputString.isSet)
      assert(!outputString.isSet)
      // errorBuffer.setLength(0)
      synchronized(isBusy = true)
      inputString.put(input)
      var ret = if (input != "quit") {
        get_output_till_prompt()
      } else {
        new Array[String](0)
      }
      // updout(ret.mkString("\n"))
      updout(ret)
      upderr(errorBuffer.toString)
      synchronized( isBusy = false )
      ret
    } catch {
      case exc: Throwable =>
        errorBuffer.append("  Main thread: " +
          (if (exc.isInstanceOf[NoSuchElementException]) "Timeout" else "Exception: " + exc))
        null
    }
  }

  def start_process(): Unit = {
    // process creation
    if (process == null) {
      val procIO = new ProcessIO(inputFn(_), outputFn(_), errorFn(_))
      val processBuilder: ProcessBuilder = Seq({if (isWindows) "tlmgr.bat" else "tlmgr"}, "--machine-readable", "shell")
      process = processBuilder.run(procIO)
    }
  }

  def get_output_till_prompt(): Array[String] = {
    var ret = ArrayBuffer[String]()
    var result = ""
    var found = false
    synchronized(isBusy = true)
    while (!found) {
      result = outputString.take()
      if (result == "tlmgr> ") {
        found = true
      } else if (result == "OK") {
        lastOk = true
        // do nothing, we found a good ok
      } else if (result == "ERROR") {
        lastOk = false
      } else {
        ret += result
      }
    }
    synchronized(isBusy = false)
    ret.toArray
  }

  def cleanup(): Unit = {
    if (process != null) {
      send_command("quit")
      process.destroy()
      // we'll need to unblock the input again
      // TODO what is that needed for???
      // if (!inputString.isSet) inputString.put("")
      // if (outputString.isSet) outputString.take()
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
        errorBuffer.append("  Input thread: Exception: " + exc + "\n")
    }
  }

  private[this] def outputFn(stdOut: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(stdOut))
    val buffer: StringBuilder = new StringBuilder()
    try {
      var line: String = ""
      while (true) {
        line = reader.readLine
        if (line == null) {
          // println("Did read NULL from stdin giving up")
          // error = true
        } else {
          // println("did read " + line + " from process")
          outputString.put(line)
        }
      }
      stdOut.close()
    } catch {
      case exc: Throwable =>
        stdOut.close()
        errorBuffer.append("  Output thread: Exception: " + exc + "\n")
    }
  }

  private[this] def errorFn(stdErr: InputStream): Unit = {
    val reader = new BufferedReader(new InputStreamReader(stdErr))
    try {
      var line = reader.readLine
      while (line != null) {
        errorBuffer.append(line + "\n")
        line = reader.readLine
      }
      stdErr.close()
    } catch {
      case exc: Throwable =>
        stdErr.close()
        errorBuffer.append("  Error thread: Exception: " + exc + "\n")
    }
  }
}
