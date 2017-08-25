// TLCockpit
// Copyright 2017 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

// import java.util.Date

import java.io.InputStream
import java.io.OutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.BufferedReader

import scalafx.beans.property.ObjectProperty
import scalafx.geometry.HPos
import scalafx.scene.control.Alert.AlertType

// needed see https://github.com/scalafx/scalafx/issues/137
import scalafx.scene.control.TableColumn._
import scalafx.scene.control.Menu._
import scalafx.Includes._
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx.event.ActionEvent
import scalafx.collections.ObservableBuffer
import scalafx.beans.property.StringProperty
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}

//import com.sun.xml.internal.bind.WhiteSpaceProcessor

import scala.sys.process._
import scala.concurrent.SyncVar
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ArrayBuilder

import scala.io.Source
//import scala.swing._


class TLPackage(name_ : String, lrev_ : String, rrev_ : String, shortdesc_ : String) {
  val name = new StringProperty(this, "name", name_)
  val shortdesc = new StringProperty(this, "shortdesc", shortdesc_)
  val lrev = new StringProperty(this, "revision", lrev_)
  val rrev = new StringProperty(this, "revision", rrev_)
}

class TlmgrProcess(updout: String => Unit, upderr: String => Unit) {
  val inputString = new SyncVar[String]                 // used for the tlmgr process input
  val outputString = new SyncVar[String]                // used for the tlmgr process output
  val errorBuffer: StringBuffer = new StringBuffer()    // buffer used for both tmgr process error console AND logging

  // set in only one place, in the main thread
  var process: Process = _
  var lastInput: String = ""
  var lastOk: Boolean = false

  def send_command(input: String): Array[String] = {

    lastInput = input

    try {
      // pass the input and wait for the output
      assert(!inputString.isSet)
      assert(!outputString.isSet)
      inputString.put(input)
      var ret = if (input != "quit") {
        get_output_till_prompt()
      } else {
        new Array[String](0)
      }
      updout(ret.mkString("\n"))
      upderr(errorBuffer.toString)
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
      } else if (result == "OK") {
        lastOk = true
        // do nothing, we found a good ok
      } else if (result == "ERROR") {
        lastOk = false
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

object ApplicationMain extends JFXApp {

  val pkgs = ArrayBuffer[TLPackage]()
  val viewpkgs = ObservableBuffer[TLPackage]()


  def onQuit(event: ActionEvent): Unit = Platform.exit()

  val outputfield = new TextArea {
    editable = false
    wrapText = true
  }
  val errorfield = new TextArea {
    editable = false
    wrapText = true
  }
  val cmdline = new TextField()
  val tlmgr = new TlmgrProcess((s:String) => outputfield.text = s,
    (s:String) => errorfield.text = s)
  tlmgr.start_process()

  // wait until we got a prompt
  val foo: Array[String] = tlmgr.get_output_till_prompt()
  // println("current output: ")
  // foo.map(println(_))

  // load the initial set of packages
  val pkglines: Array[String] = tlmgr.send_command("info --only-installed --data name,localrev,shortdesc")
  pkglines.map(line => {
    val fields: Array[String] = line.split(",",-1)
    val sd = if (fields(2).isEmpty) "" else fields(2).substring(1).dropRight(1).replace("""\"""",""""""")
    pkgs += new TLPackage(fields(0),fields(1),"0",sd)
  })
  viewpkgs.clear()
  pkgs.map(viewpkgs += _)

  def callback_quit(): Unit = {
    tlmgr.cleanup()
    Platform.exit()
    sys.exit(0)
  }

  def callback_load(s: String): Unit = {
    val foo = tlmgr.send_command("load " + s)
    if (s == "remote") {
      val pkglines: Array[String] = tlmgr.send_command("info --data name,localrev,remoterev,shortdesc")
      val newpkgs = ArrayBuffer[TLPackage]()
      pkglines.map(line => {
        val fields: Array[String] = line.split(",", -1)
        val sd = fields(3)
        val shortdesc = if (sd.isEmpty) "" else sd.substring(1).dropRight(1).replace("""\"""",""""""")
        newpkgs += new TLPackage(fields(0), fields(1), fields(2), shortdesc)
      })
      pkgs.clear()
      newpkgs.map(pkgs += _)
      viewpkgs.clear()
      pkgs.map(viewpkgs += _)
    }
    // println("got result from load " + s + ": ")
    // foo.map(println(_))
  }

  def callback_run_cmdline(): Unit = {
    tlmgr.send_command(cmdline.text.value)
  }

  def callback_list_collections(): Unit = {
    val foo = tlmgr.send_command("info collections")
    // println("got result from info collections: ")
    // foo.map(println(_))
    outputfield.text = foo.mkString("\n")
    errorfield.text = tlmgr.errorBuffer.toString
  }

  def not_implemented_info(): Unit = {
    new Alert(AlertType.Warning) {
      initOwner(stage)
      title = "Warning"
      headerText = "This functionality is not implemented by now!"
      contentText = "Sorry for the inconveniences."
    }.showAndWait()
  }

  def callback_about(): Unit = {
    new Alert(AlertType.Information) {
      initOwner(stage)
      title = "About TLCockpit"
      headerText = "TLCockpit version 0.1\nLicense: GPL3+"
      contentText = "Brought to you by Norbert\nSources: https://github.com/TeX-Live/tlcockpit"
    }.showAndWait()
  }
  def callback_show_all() : Unit = {
    viewpkgs.clear()
    pkgs.map(viewpkgs += _)
  }
  def callback_show_installed() : Unit = {
    viewpkgs.clear()
    pkgs.map(p => if (p.lrev.value.toInt > 0) viewpkgs += p)
  }
  def callback_show_updates(): Unit = {
    val newpkgs = ArrayBuffer[TLPackage]()
    pkgs.map(p =>
      // TODO we are not dealing with not integer values by now!
      if (p.lrev.value.toInt > 0 && p.lrev.value.toInt < p.rrev.value.toInt) {
        newpkgs += p
      }
    )
    viewpkgs.clear()
    newpkgs.map(viewpkgs += _)
  }
  def callback_show_pkg_info(pkg: String): Unit = {
    val pkginfo = tlmgr.send_command(s"info $pkg")
    val dialog = new Dialog() {
      initOwner(stage)
      title = s"Package Information for $pkg"
      headerText = s"Package Information for $pkg"
      resizable = true
    }
    dialog.dialogPane().buttonTypes = Seq(ButtonType.OK)
    val grid = new GridPane() {
      hgap = 10
      vgap = 10
      padding = Insets(20)
    }
    var crow = 0
    pkginfo.map((line:String) => {
      val keyval = line.split(":",2).map(_.trim)
      if (keyval.length == 2) {
        val keylabel = new Label(keyval(0))
        val vallabel = new Label(keyval(1))
        vallabel.wrapText = true
        grid.add(keylabel, 0, crow)
        grid.add(vallabel, 1, crow)
        crow += 1
      }
    })
    grid.columnConstraints = Seq(new ColumnConstraints(100,150,200),new ColumnConstraints(100,300,5000,Priority.Always,new HPos(HPos.Left),true))
    dialog.dialogPane().content = grid
    dialog.width = 500
    dialog.showAndWait()
  }
  stage = new PrimaryStage {
    title = "TLCockpit"
    scene = new Scene {
      root = {
        val topBox = new MenuBar {
          useSystemMenuBar = true
          menus.add(new Menu("TLCockpit") {
            items = List(
              new MenuItem("Load default (from tlpdb) repository") {
                onAction = (ae: ActionEvent) => callback_load("remote")
              },
              new MenuItem("Load standard net repository") {
                onAction = (ae: ActionEvent) => not_implemented_info()
              },
              new MenuItem("Load other repository ...") {
                onAction = (ae: ActionEvent) => not_implemented_info()
              },
              new SeparatorMenuItem,
              new MenuItem("Exit") {
                onAction = (ae: ActionEvent) => callback_quit()
              }
            )
          })
          menus.add(new Menu("Options") {
            items = List(
              new MenuItem("General ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Paper ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Platforms ...") { onAction = (ae) => not_implemented_info() },
              new SeparatorMenuItem,
              new CheckMenuItem("Expert options"),
              new CheckMenuItem("Enable debugging options"),
              new CheckMenuItem("Disable auto-install of new packages"),
              new CheckMenuItem("Disable auto-removal of server-deleted packages")
            )
          })
          menus.add(new Menu("Actions") {
            items = List(
              new MenuItem("Update filename database ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Rebuild all formats ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Update font map database ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Restore packages from backup ...") { onAction = (ae) => not_implemented_info() },
              new MenuItem("Handle symlinks in system dirs ...") { onAction = (ae) => not_implemented_info() },
              new SeparatorMenuItem,
              new MenuItem("Remove TeX Live ...") { onAction = (ae) => not_implemented_info() }
            )
          })
          menus.add(new Menu("Help") {
            items = List(
              new MenuItem("Manual") { onAction = (ae) => not_implemented_info() },
              new MenuItem("About") { onAction = (ae) => callback_about() },
            )
          })
        }
        val centerBox = new VBox {
          children = List(
            new TitledPane {
              text = "Actions"
              collapsible = false
              content = new VBox {
                spacing = 10
                children = List(
                  new HBox {
                    spacing = 10
                    children = List(
                      cmdline,
                      new Button {
                        text = "Go"
                        onAction = (event: ActionEvent) => callback_run_cmdline()
                      },
                      new Button {
                        text = "Load repository";
                        onAction = (event: ActionEvent) => callback_load("remote")
                      }
                    )
                  },
                  new HBox {
                    spacing = 10
                    children = List(
                      new Button {
                        text = "Show updates"
                        onAction = (e: ActionEvent) => callback_show_updates()
                      },
                      new Button {
                        text = "Show installed"
                        onAction = (e: ActionEvent) => callback_show_installed()
                      },
                      new Button {
                        text = "Show all"
                        onAction = (e: ActionEvent) => callback_show_all()
                      }
                    )
                  }
                )
              }
            },
            new TitledPane {
              text = "Debug"
              collapsible = true
              expanded = false
              content = new TabPane {
                minWidth = 400
                tabs = Seq(
                  new Tab {
                    text = "Output"
                    closable = false
                    content = outputfield
                  },
                  new Tab {
                    text = "Error"
                    closable = false
                    content = errorfield
                  }
                )
              }
            }, {
              val col1 = new TableColumn[TLPackage, String] {
                text = "Package"
                cellValueFactory = {
                  _.value.name
                }
                prefWidth = 150
              }
              val col2 = new TableColumn[TLPackage, String] {
                text = "Local rev"
                cellValueFactory = {
                  _.value.lrev
                }
                prefWidth = 100
              }
              val col3 = new TableColumn[TLPackage, String] {
                text = "Remote rev"
                cellValueFactory = {
                  _.value.rrev
                }
                prefWidth = 100
              }
              val col4 = new TableColumn[TLPackage, String] {
                text = "Description"
                cellValueFactory = {
                  _.value.shortdesc
                }
                prefWidth = 300
              }
              val table = new TableView[TLPackage](viewpkgs) {
                columns ++= List(col1,col2,col3,col4)
              }
              col4.prefWidth.bind(table.width - col1.width - col2.width - col3.width)
              table.rowFactory = { _ =>
                val row = new TableRow[TLPackage] {}
                row.onMouseClicked = { (me: MouseEvent) => callback_show_pkg_info(row.item.value.name.value) }
                row
              }
              table
            },
          )
        }
        val bottomBox = new HBox {
          spacing = 10
          children = List(
            new Button {
              text = "Quit"
              onAction = (event: ActionEvent) => callback_quit()
            }
          )
        }

        new BorderPane {
          padding = Insets(20)
          top = topBox
          // left = leftBox
          center = centerBox
          bottom = bottomBox
        }
      }
    }
  }
}  // object ApplicationMain

// vim:set tabstop=2 expandtab : //
