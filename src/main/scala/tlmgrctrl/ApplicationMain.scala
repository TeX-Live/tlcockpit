package tlmgrctrl

// import java.util.Date

import java.io.InputStream
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.BufferedReader

//import com.sun.xml.internal.bind.WhiteSpaceProcessor

import scala.sys.process._
import scala.concurrent.SyncVar
import scala.collection.mutable.ArrayBuffer
import scala.swing._


class UI(tlmgr: TlmgrProcess) extends MainFrame {
	title = "TeX Live Manager GUI2"

	def restrictHeight(s: Component): Unit = {
		s.maximumSize = new Dimension(Short.MaxValue, s.preferredSize.height)
	}

	val cmdline = new TextField {
		columns = 32
	}
	val outputfield = new TextArea {
		rows = 8;
		lineWrap = true;
		wordWrap = true;
		editable = false
	}
	val errorfield = new TextArea {
		rows = 8;
		lineWrap = true;
		wordWrap = true;
		editable = false
	}
	val runbtn = Button("Run") {
		callback_run_cmdline()
	}

	restrictHeight(cmdline)
	contents = new BoxPanel(Orientation.Vertical) {
		contents += new BoxPanel(Orientation.Horizontal) {
			contents += new Label("TeX Live Manager")
		}
		contents += Swing.VStrut(10)
		contents += Swing.Glue
		contents += new BoxPanel(Orientation.Horizontal) {
			contents += new Label("Type command:")
			contents += Swing.HStrut(5)
			contents += cmdline
			contents += Swing.HStrut(5)
			contents += runbtn
		}
		contents += Swing.VStrut(5)
		contents += new TabbedPane() {
			pages += new TabbedPane.Page("Output", new ScrollPane(outputfield))
			pages += new TabbedPane.Page("Error", new ScrollPane(errorfield))
		}
		// contents += new ScrollPane(outputfield)
		// contents += Swing.VStrut(5)
		// contents += new ScrollPane(errorfield)

		contents += Swing.VStrut(5)
		contents += new BoxPanel(Orientation.Horizontal) {
			contents += Button("Load local") {
				callback_load("local")
			}
			contents += Swing.HStrut(5)
			contents += Button("Load remote") {
				callback_load("remote")
			}
			contents += Swing.HStrut(5)
			contents += Button("List collections") {
				callback_list_collections()
			}
			contents += Swing.HStrut(5)
			contents += Button("Quit") {
				callback_quit()
			}
		}
		border = Swing.EmptyBorder(10, 10, 10, 10)
	}

	def callback_load(s: String): Unit = {
		val foo = tlmgr.send_command("load " + s)
		// println("got result from load " + s + ": ")
		// foo.map(println(_))
		outputfield.text = foo.mkString("\n")
		errorfield.text = tlmgr.errorBuffer.toString
	}

	def callback_quit(): Unit = {
		tlmgr.cleanup()
		close()
		sys.exit(0)
	}

	def callback_run_cmdline(): Unit = {
		val foo = tlmgr.send_command(cmdline.text)
		// println("got result from " + cmdline.text + ": ")
		// foo.map(println(_))
		outputfield.text = foo.mkString("\n")
		errorfield.text = tlmgr.errorBuffer.toString
	}

	def callback_list_collections(): Unit = {
		val foo = tlmgr.send_command("info collections")
		// println("got result from info collections: ")
		// foo.map(println(_))
		outputfield.text = foo.mkString("\n")
		errorfield.text = tlmgr.errorBuffer.toString
	}
}

class TlmgrProcess() {
	val inputString = new SyncVar[String]                 // used for the tlmgr process input
	val outputString = new SyncVar[String]                // used for the tlmgr process output
	val errorBuffer: StringBuffer = new StringBuffer()    // buffer used for both tlmgr process error console AND logging

	// set in only one place, in the main thread
	var process: Process = _
	var lastInput: String = ""

	def send_command(input: String): Array[String] = {

		lastInput = input

		try {
			// pass the input and wait for the output
			assert(!inputString.isSet)
			assert(!outputString.isSet)
			inputString.put(input)
			if (input != "quit") {
				get_output_till_prompt()
			} else {
				new Array[String](0)
			}
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
			val processBuilder: ProcessBuilder = Seq("tlmgr", "--machine-readable", "shell")
			process = processBuilder.run(procIO)
		}
	}

	def get_output_till_prompt(): Array[String] = {
		var ret = ArrayBuffer[String]()
		var result = ""
		var found = false
		while (!found) {
			result = outputString.take()
			if (result == "tlmgr> ") {
				found = true
			} else {
				ret += result
			}
		}
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

object ApplicationMain {
	def main(args: Array[String]): Unit = {
		var tlmgrProcess = new TlmgrProcess()
		tlmgrProcess.start_process()
		var foo: Array[String] = tlmgrProcess.get_output_till_prompt()
		// println("current output: ")
		// foo.map(println(_))
		// UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName)
		val ui = new UI(tlmgrProcess)
		ui.visible = true
	}
}  // object ApplicationMain
