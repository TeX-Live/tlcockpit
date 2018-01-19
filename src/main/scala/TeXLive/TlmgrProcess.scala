// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TeXLive

import java.io._

import TeXLive.OsTools._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.SyncVar
import scala.sys.process._

class TlmgrProcess(updout: String => Unit, upderr: String => Unit)  extends LazyLogging  {
  val inputString = new SyncVar[String]                 // used for the tlmgr process input
  // val outputString = new SyncVar[String]                // used for the tlmgr process output
  // val errorBuffer: StringBuffer = new StringBuffer()    // buffer used for both tmgr process error console AND logging

  val tlroot = "kpsewhich -var-value SELFAUTOPARENT".!!.trim
  logger.debug("tlroot ==" + tlroot + "==")

  // set in only one place, in the main thread
  var process: Process = _

  def send_command(input: String): Unit = {
    logger.debug(s"send_command: ${input}")
    try {
      assert(!inputString.isSet)
      inputString.put(input)
    } catch {
      case exc: Throwable =>
        logger.warn("Main thread: " +
          (if (exc.isInstanceOf[NoSuchElementException]) "Timeout" else "Exception: " + exc))
    }
  }

  def isAlive(): Boolean = {
    if (process != null)
      process.isAlive()
    else
      // return true on not-started process
      true
  }

  def start_process(): Boolean = {
    logger.debug("tlmgr process: entering starting process")
    // process creation
    if (process == null) {
      logger.debug("tlmgr process: start_process: creating procIO")
      val procIO = new ProcessIO(inputFn(_), outputFn(_, updout), outputFn(_, upderr))
      val processBuilder: ProcessBuilder = Seq({if (isWindows) "tlmgr.bat" else "tlmgr"}, "-v", "--machine-readable", "shell")
      logger.debug("tlmgr process: start_process: running new tlmgr process")
      process = processBuilder.run(procIO)
    }
    logger.debug("tlmgr process: start_process: checking for being alive")
    process.isAlive()
  }

  def cleanup(): Unit = {
    if (process != null) {
      logger.debug("tlmgr process: cleanup - sending quit")
      send_command("quit")
      logger.debug("tlmgr process: cleanup - sleeping 2s")
      Thread.sleep(1000)
      logger.debug("tlmgr process: cleanup - destroying process")
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
          logger.trace("writing " + input + " to process")
          writer.flush()
        }
      }
      stdin.close()
    } catch {
      case exc: Throwable =>
        stdin.close()
        logger.warn("Exception in inputFn thread: " + exc + "\n")
    }
  }

  private[this] def outputFn(outStr: InputStream, updfun: String => Unit): Unit = {
    val reader = new BufferedReader(new InputStreamReader(outStr))
    try {
      var line: String = ""
      while (true) {
        line = reader.readLine
        logger.trace("DDD did read " + line + " from process")
        try {
          updfun(line)
        } catch {
          case exc: Throwable =>
            logger.debug("Update output line function failed, continuing anyway. Exception: " + exc)
        }
      }
      outStr.close()
    } catch {
      case exc: Throwable =>
        outStr.close()
        logger.warn("Exception in outputFn thread: " + exc + "\n")
    }
  }
}
