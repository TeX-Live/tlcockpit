// TLCockpit
// Copyright 2017-2026 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TeXLive

import java.io._
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

import TeXLive.OsTools._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.SyncVar
import scala.sys.process._

class TlmgrProcess(updout: String => Unit, upderr: String => Unit)  extends LazyLogging  {
  val inputString = new SyncVar[String]                    // used for the tlmgr process input
  val outputQueue = new ArrayBlockingQueue[String](256)     // stdout queue with timeout support
  val errorQueue  = new ArrayBlockingQueue[String](256)     // stderr queue with timeout support

  val tlroot: String = "kpsewhich -var-value SELFAUTOPARENT".!!.trim
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
      true
  }

  /** Non-blocking poll for a stdout line; returns None if timeout expired. */
  def takeOutput(timeoutMs: Int): Option[String] = {
    val s = outputQueue.poll(timeoutMs.toLong, TimeUnit.MILLISECONDS)
    if (s != null) Some(s) else None
  }

  /** Non-blocking poll for a stderr line; returns None if timeout expired. */
  def takeError(timeoutMs: Int): Option[String] = {
    val s = errorQueue.poll(timeoutMs.toLong, TimeUnit.MILLISECONDS)
    if (s != null) Some(s) else None
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
        logger.warn("Exception in inputFn thread: " + exc + "\n")
        stdin.close()
    }
  }

  /* The standard output passing function (stdout) */
  private[this] def outputFn(outStr: InputStream, updfun: String => Unit): Unit = {
    val reader = new BufferedReader(new InputStreamReader(outStr))
    try {
      var line: String = ""
      while (true) {
        line = reader.readLine
        logger.trace("DDD did read " + line + " from process")
        try {
          updfun(line)
          outputQueue.put(line)
        } catch {
          case exc: Throwable =>
            logger.debug("Update output line function failed, continuing anyway. Exception: " + exc)
        }
      }
      outStr.close()
    } catch {
      case exc: Throwable =>
        logger.warn("Exception in outputFn thread: " + exc + "\n")
        outStr.close()
    }
  }

  /* The stderr passing function */
  private[this] def errorFn(errStr: InputStream, updfun: String => Unit): Unit = {
    val reader = new BufferedReader(new InputStreamReader(errStr))
    try {
      var line: String = ""
      while (true) {
        line = reader.readLine
        logger.trace("DDD did read " + line + " from stderr")
        try {
          updfun(line)
          errorQueue.put(line)
        } catch {
          case exc: Throwable =>
            logger.debug("Update error line function failed, continuing anyway. Exception: " + exc)
        }
      }
      errStr.close()
    } catch {
      case exc: Throwable =>
        logger.warn("Exception in errorFn thread: " + exc + "\n")
        errStr.close()
    }
  }

  def start_process(): Boolean = {
    logger.debug("tlmgr process: entering starting process")
    if (process == null) {
      logger.debug("tlmgr process: start_process: creating procIO")
      val procIO = new ProcessIO(inputFn, outputFn(_, updout), errorFn(_, upderr))
      val startCmd =
        if (isWindows) {
          logger.debug("detected Windows, using tlmgr.bat")
          Seq("tlmgr.bat", "-v", "--machine-readable", "shell")
        } else if (isCygwin) {
          logger.debug("detected Cygwin, using bash -l -c tlmgr")
          Seq("bash", "-l", "-c", "tlmgr -v --machine-readable shell")
        } else {
          logger.debug("detected Unixish, using tlmgr")
          Seq("tlmgr", "-v", "--machine-readable", "shell")
        }
      val processBuilder: ProcessBuilder = startCmd
      logger.debug("tlmgr process: start_process: running new tlmgr process")
      process = processBuilder.run(procIO)
    }
    logger.debug("tlmgr process: start_process: checking for being alive")
    process.isAlive()
  }
}
