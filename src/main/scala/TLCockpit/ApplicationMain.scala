// TLCockpit
// Copyright 2017 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

import TLCockpit.Utils._
import TeXLive._

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise, SyncVar}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.sys.process._
import scalafx.beans.property.BooleanProperty
import scalafx.scene.text.Font
// ScalaFX imports
import scalafx.event.Event
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.geometry.{Pos, Orientation}
import scalafx.scene.Cursor
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.input.{KeyCode, KeyEvent, MouseEvent}
import scalafx.scene.paint.Color
// needed see https://github.com/scalafx/scalafx/issues/137
import scalafx.scene.control.TableColumn._
import scalafx.scene.control.TreeItem._
import scalafx.scene.control.TreeTableColumn._
import scalafx.scene.control.TreeItem
import scalafx.scene.control.Menu._
import scalafx.scene.control.ListCell
import scalafx.Includes._
import scalafx.application.{JFXApp, Platform}
import scalafx.application.JFXApp.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.layout._
import scalafx.scene.control._
import scalafx.event.ActionEvent
import scalafx.collections.ObservableBuffer
import scalafx.collections.ObservableMap

// JSON support - important load TLPackageJsonProtocol later!
import spray.json._
import TeXLive.JsonProtocol._


object ApplicationMain extends JFXApp {

  val version: String = getClass.getPackage.getImplementationVersion

  var tlmgrBusy = BooleanProperty(false)

  // necessary action when Window is closed with X or some other operation
  override def stopApp(): Unit = {
    tlmgr.cleanup()
  }

  val iconImage = new Image(getClass.getResourceAsStream("tlcockpit-48.jpg"))
  val logoImage = new Image(getClass.getResourceAsStream("tlcockpit-128.jpg"))

  val tlpkgs: ObservableMap[String, TLPackage] = ObservableMap[String,TLPackage]()
  val pkgs: ObservableMap[String, TLPackageDisplay] = ObservableMap[String, TLPackageDisplay]()
  val upds: ObservableMap[String, TLUpdateDisplay] = ObservableMap[String, TLUpdateDisplay]()
  val bkps: ObservableMap[String, Map[String, TLBackupDisplay]] = ObservableMap[String, Map[String,TLBackupDisplay]]()  // pkgname -> (version -> TLBackup)*

  val logText: ObservableBuffer[String] = ObservableBuffer[String]()
  val outputText: ObservableBuffer[String] = ObservableBuffer[String]()
  val errorText: ObservableBuffer[String] = ObservableBuffer[String]()

  val outputfield: TextArea = new TextArea {
    editable = false
    wrapText = true
    text = ""
  }
  val logfield: TextArea = new TextArea {
    editable = false
    wrapText = true
    text = ""
  }
  val errorfield: TextArea = new TextArea {
    editable = false
    wrapText = true
    text = ""
  }
  logText.onChange({
    logfield.text = logText.mkString("\n")
    logfield.scrollTop = Double.MaxValue
  })
  errorText.onChange({
    errorfield.text = errorText.mkString("\n")
    errorfield.scrollTop = Double.MaxValue
    if (errorfield.text.value.nonEmpty) {
      outerrpane.expanded = true
      outerrtabs.selectionModel().select(2)
    }
  })
  outputText.onChange({
    outputfield.text = outputText.mkString("\n")
    outputfield.scrollTop = Double.MaxValue
  })

  val update_all_menu: MenuItem = new MenuItem("Update all") {
    val cmd = "--all" + {
      if (disable_auto_install) " --no-auto-install" else "" } + {
      if (disable_auto_removal) " --no-auto-remove" else "" } + {
      if (enable_reinstall_forcible) " --reinstall-forcibly-removed" else "" }
    onAction = (ae) => callback_update(cmd)
    disable = true
  }
  val update_self_menu: MenuItem = new MenuItem("Update self") {
    onAction = (ae) => callback_update("--self")
    disable = true
  }

  val outerrtabs: TabPane = new TabPane {
    minWidth = 400
    tabs = Seq(
      new Tab {
        text = "Output"
        closable = false
        content = outputfield
      },
      new Tab {
        text = "Logging"
        closable = false
        content = logfield
      },
      new Tab {
        text = "Errors"
        closable = false
        content = errorfield
      }
    )
  }
  val outerrpane: TitledPane = new TitledPane {
    text = "Debug"
    collapsible = true
    expanded = false
    content = outerrtabs
  }

  val cmdline = new TextField()
  cmdline.onKeyPressed = {
    (ae: KeyEvent) => if (ae.code == KeyCode.Enter) callback_run_cmdline()
  }
  val outputLine = new SyncVar[String]
  val errorLine  = new SyncVar[String]


  def callback_quit(): Unit = {
    tlmgr.cleanup()
    Platform.exit()
    sys.exit(0)
  }

  def callback_run_text(s: String): Unit = {
    tlmgr_send(s, (a: String, b: Array[String]) => {})
  }

  def callback_run_cmdline(): Unit = {
    tlmgr_send(cmdline.text.value, (status,output) => {
      outputText.append(output.mkString("\n"))
      outerrpane.expanded = true
      outerrtabs.selectionModel().select(0)
    })
  }

  def not_implemented_info(): Unit = {
    new Alert(AlertType.Warning) {
      initOwner(stage)
      title = "Warning"
      headerText = "This functionality is not implemented by now!"
      contentText = "Sorry for the inconveniences."
    }.showAndWait()
  }

  val OutputBuffer: ObservableBuffer[String] = ObservableBuffer[String]()
  var OutputBufferIndex:Int = 0
  val OutputFlushLines = 100
  OutputBuffer.onChange {
    // length is number of lines!
    var foo = ""
    OutputBuffer.synchronized(
      if (OutputBuffer.length - OutputBufferIndex > OutputFlushLines) {
        foo = OutputBuffer.slice(OutputBufferIndex, OutputBufferIndex + OutputFlushLines).mkString("")
        OutputBufferIndex += OutputFlushLines
        Platform.runLater {
          outputText.append(foo)
        }
      }
    )
  }
  def reset_output_buffer(): Unit = {
    OutputBuffer.clear()
    OutputBufferIndex = 0
  }

  def callback_run_external(s: String, unbuffered: Boolean = true): Unit = {
    outputText.clear()
    // logText.clear()
    outerrpane.expanded = true
    outerrtabs.selectionModel().select(0)
    outputText.append(s"Running $s" + (if (unbuffered) " (unbuffered)" else " (buffered)"))
    val foo = Future {
      s ! ProcessLogger(
        line => if (unbuffered) Platform.runLater( outputText.append(line) )
                else OutputBuffer.synchronized( OutputBuffer.append(line + "\n") ),
        line => Platform.runLater( logText.append(line) )
      )
    }
    foo.onComplete {
      case Success(ret) =>
        Platform.runLater {
          outputText.append(OutputBuffer.slice(OutputBufferIndex,OutputBuffer.length).mkString(""))
          outputText.append("Completed")
          reset_output_buffer()
          outputfield.scrollTop = Double.MaxValue
        }
      case Failure(t) =>
        Platform.runLater {
          outputText.append(OutputBuffer.slice(OutputBufferIndex,OutputBuffer.length).mkString(""))
          outputText.append("Completed")
          reset_output_buffer()
          outputfield.scrollTop = Double.MaxValue
          errorText.append("An ERROR has occurred running $s: " + t.getMessage)
          errorfield.scrollTop = Double.MaxValue
          outerrpane.expanded = true
          outerrtabs.selectionModel().select(2)
        }
    }
  }

  def callback_about(): Unit = {
    new Alert(AlertType.Information) {
      initOwner(stage)
      title = "About TLCockpit"
      graphic = new ImageView(logoImage)
      headerText = "TLCockpit version " + version + "\n\nManage your TeX Live with speed!"
      contentText = "Copyright 2017 Norbert Preining\nLicense: GPL3+\nSources: https://github.com/TeX-Live/tlcockpit"
    }.showAndWait()
  }

  def set_line_update_function(mode: String) = {
    var prevName = ""
    stdoutLineUpdateFunc = (l:String) => {
      // println("DEBUG line update: " + l + "=")
      l match {
        case u if u.startsWith("location-url") => None
        case u if u.startsWith("total-bytes") => None
        case u if u.startsWith("end-of-header") => None
        // case u if u.startsWith("end-of-updates") => None
        case u if u == "OK" => None
        case u if u.startsWith("tlmgr>") => None
        case u =>
          if (prevName != "") {
            if (mode == "update") {
              // println("DEBUG Removing " + prevUpdName + " from list!")
              upds.remove(prevName)
            } else if (mode == "remove") {
              tlpkgs(prevName).installed = false
            } else { // install
              tlpkgs(prevName).installed = true
            }
            if (mode == "remove") {
              pkgs(prevName).lrev = ObjectProperty[Int](0)
              pkgs(prevName).installed = StringProperty("Not installed")
              tlpkgs(prevName).lrev = 0
            } else { // install and update
              pkgs(prevName).lrev = pkgs(prevName).rrev
              pkgs(prevName).installed = StringProperty("Installed") // TODO support Mixed!!!
              tlpkgs(prevName).lrev = tlpkgs(prevName).rrev
            }
            Platform.runLater {
              if (mode == "update") updateTable.refresh()
              packageTable.refresh()
            }
          }
          if (u.startsWith("end-of-updates")) {
            // nothing to be done, all has been done above
            // println("DEBUG got end of updates")
          } else {
            // println("DEBUG getting update line")
            prevName = if (mode == "update") {
              val foo = parse_one_update_line(l)
              val pkgname = foo.name.value
              upds(pkgname).status = StringProperty("Updating ...")
              updateTable.refresh()
              pkgname
            } else if (mode == "install") {
              val fields = l.split("\t")
              val pkgname = fields(0)
              pkgs(pkgname).installed = StringProperty("Installing ...")
              packageTable.refresh()
              pkgname
            } else { // remove
              val fields = l.split("\t")
              val pkgname = fields(0)
              pkgs(pkgname).installed = StringProperty("Removing ...")
              packageTable.refresh()
              pkgname
            }
          }
      }
    }
  }

  def callback_update(s: String): Unit = {
    set_line_update_function("update")
    val cmd = if (s == "--self") "update --self" else s"update $s"
    tlmgr_send(cmd, (a,b) => {
      stdoutLineUpdateFunc = defaultStdoutLineUpdateFunc
      if (s == "--self") {
        reinitialize_tlmgr()
        // this doesn't work seemingly
        // update_upds_list()
      }
    })
  }

  def callback_remove(pkg: String): Unit = {
    set_line_update_function("remove")
    tlmgr_send(s"remove $pkg", (_, _) => { stdoutLineUpdateFunc = defaultStdoutLineUpdateFunc })
  }
  def callback_install(pkg: String): Unit = {
    set_line_update_function("install")
    tlmgr_send(s"install $pkg", (_,_) => { stdoutLineUpdateFunc = defaultStdoutLineUpdateFunc })
  }


  def callback_restore(str: String, rev: String): Unit = {
    tlmgr_send(s"restore --force $str $rev", (_,_) => {
      tlpkgs(str).lrev = rev.toLong
      pkgs(str).lrev = ObjectProperty[Int](rev.toInt)
      packageTable.refresh()
    })
  }

  bkps.onChange( (obs,chs) => {
    val doit = chs match {
      case ObservableMap.Add(k, v) => k.toString == "root"
      case ObservableMap.Replace(k, va, vr) => k.toString == "root"
      case ObservableMap.Remove(k, v) => k.toString == "root"
    }
    if (doit) {
      // println("DEBUG bkps.onChange called new length = " + bkps.keys.toArray.length)
      val newroot = new TreeItem[TLBackupDisplay](new TLBackupDisplay("root", "", "")) {
        children = bkps
          .filter(_._1 != "root")
          .map(p => {
            val pkgname: String = p._1
            // we sort by negative of revision number, which give inverse sort
            val versmap: Array[(String, TLBackupDisplay)] = p._2.toArray.sortBy(-_._2.rev.value.toInt)

            val foo: Seq[TreeItem[TLBackupDisplay]] = versmap.tail.sortBy(-_._2.rev.value.toInt).map { q =>
              new TreeItem[TLBackupDisplay](q._2)
            }.toSeq
            new TreeItem[TLBackupDisplay](versmap.head._2) {
              children = foo
            }
          }).toArray.sortBy(_.value.value.name.value)
      }
      Platform.runLater {
        backupTable.root = newroot
      }
    }
  })

  def view_pkgs_by_collections(pkgbuf: scala.collection.mutable.Map[String, TLPackageDisplay],
                               binbuf: scala.collection.mutable.Map[String, ArrayBuffer[TLPackageDisplay]],
                               colbuf: scala.collection.mutable.Map[String, ArrayBuffer[TLPackageDisplay]]): Seq[TreeItem[TLPackageDisplay]] = {
    val bin_pkg_map = compute_bin_pkg_mapping(pkgbuf, binbuf)
    colbuf.map(
      p => {
        val colname: String = p._1
        val coldeps: Seq[TLPackageDisplay] = p._2
        val coltlpd: TLPackageDisplay = pkgbuf(colname)

        new TreeItem[TLPackageDisplay](coltlpd) {
            children = coldeps.filter(q => tlpkgs(q.name.value).category != "Collection").sortBy(_.name.value).map(sub => {
              val binmap: (Boolean, Seq[TLPackageDisplay]) = bin_pkg_map(sub.name.value)
              val ismixed: Boolean = binmap._1
              val kids: Seq[TLPackageDisplay] = binmap._2.sortBy(_.name.value)
              val ti = if (ismixed) {
                // replace installed status with "Mixed"
                new TreeItem[TLPackageDisplay](
                  new TLPackageDisplay(sub.name.value, sub.lrev.value.toString, sub.rrev.value.toString, sub.shortdesc.value, sub.size.value.toString, "Mixed")
                ) {
                  children = kids.map(new TreeItem[TLPackageDisplay](_))
                }
              } else {
                new TreeItem[TLPackageDisplay](sub) {
                  children = kids.map(new TreeItem[TLPackageDisplay](_))
                }
              }
              ti
            }
            )
          }
      }
    ).toSeq
    // ArrayBuffer.empty[TreeItem[TLPackageDisplay]]
  }

  def view_pkgs_by_names(pkgbuf: scala.collection.mutable.Map[String, TLPackageDisplay],
                         binbuf: scala.collection.mutable.Map[String, ArrayBuffer[TLPackageDisplay]]): Seq[TreeItem[TLPackageDisplay]] = {
    val bin_pkg_map: Map[String, (Boolean, Seq[TLPackageDisplay])] = compute_bin_pkg_mapping(pkgbuf, binbuf)
    pkgbuf.map{
      p => {
        val binmap: (Boolean, Seq[TLPackageDisplay]) = bin_pkg_map(p._1)
        val pkgtlp: TLPackageDisplay = p._2
        val ismixed: Boolean = binmap._1
        val kids: Seq[TLPackageDisplay] = binmap._2.sortBy(_.name.value)
        if (ismixed) {
          new TreeItem[TLPackageDisplay](
            new TLPackageDisplay(pkgtlp.name.value, pkgtlp.lrev.value.toString, pkgtlp.rrev.value.toString, pkgtlp.shortdesc.value, pkgtlp.size.value.toString, "Mixed")
          ) {
            children = kids.map(new TreeItem[TLPackageDisplay](_))
          }
        } else {
          new TreeItem[TLPackageDisplay](pkgtlp) {
            children = kids.map(new TreeItem[TLPackageDisplay](_))
          }
        }
      }
    }.toSeq
  }

  def compute_bin_pkg_mapping(pkgbuf: scala.collection.mutable.Map[String, TLPackageDisplay],
                              binbuf: scala.collection.mutable.Map[String, ArrayBuffer[TLPackageDisplay]]): Map[String, (Boolean, Seq[TLPackageDisplay])] = {
    pkgbuf.map {
      p => {
        val kids: Seq[TLPackageDisplay] = if (binbuf.keySet.contains(p._2.name.value)) {
          binbuf(p._2.name.value)
        } else {
          Seq()
        }
        // for ismixed we && all the installed status. If all are installed, we get true
        val allinstalled = (kids :+ p._2).foldRight[Boolean](true)((k, b) => k.installed.value == "Installed" && b)
        val someinstalled = (kids :+ p._2).exists(_.installed.value == "Installed")
        val mixedinstalled = !allinstalled && someinstalled
        (p._1, (mixedinstalled, kids))
      }
    }.toMap
  }
  pkgs.onChange( (obs,chs) => {
    val doit = chs match {
      case ObservableMap.Add(k, v) => k.toString == "root"
      case ObservableMap.Replace(k, va, vr) => k.toString == "root"
        // don't call the trigger on root removal!
      // case ObservableMap.Remove(k, v) => k.toString == "root"
      case ObservableMap.Remove(k,v) => false
    }
    if (doit) {
      // println("DEBUG: entering pkgs.onChange")
      // val pkgbuf: ArrayBuffer[TLPackageDisplay] = ArrayBuffer.empty[TLPackageDisplay]
      val pkgbuf = scala.collection.mutable.Map.empty[String, TLPackageDisplay]
      val binbuf = scala.collection.mutable.Map.empty[String, ArrayBuffer[TLPackageDisplay]]
      val colbuf = scala.collection.mutable.Map.empty[String, ArrayBuffer[TLPackageDisplay]]
      pkgs.foreach(pkg => {
        // complicated part, determine whether it is a sub package or not!
        // we strip of initial texlive. prefixes to make sure we deal
        // with real packages
        if ((pkg._1.startsWith("texlive.infra") && pkg._1.stripPrefix("texlive.infra").contains(".")) ||
            pkg._1.stripPrefix("texlive.infra").contains(".")) {
          val foo: Array[String] = if (pkg._1.startsWith("texlive.infra"))
            Array("texlive.infra", pkg._1.stripPrefix("texlive.infra"))
          else
            pkg._1.split('.')
          val pkgname = foo(0)
          if (pkgname != "") {
            val binname = foo(1)
            if (binbuf.keySet.contains(pkgname)) {
              binbuf(pkgname) += pkg._2
            } else {
              binbuf(pkgname) = ArrayBuffer[TLPackageDisplay](pkg._2)
            }
          }
        } else if (pkg._1 == "root") {
          // ignore the dummy root element,
          // only used for speeding up event handling
        } else {
          pkgbuf(pkg._1) = pkg._2
        }
      })
      // Another round to propagate purely .win32 packages like wintools.win32 or
      // dviout.win32 from binpkg status to full pkg, since they don't have
      // accompanying main packages
      binbuf.foreach(p => {
        if (!pkgbuf.contains(p._1)) {
          if (p._2.length > 1) {
            errorText += "THAT SHOULD NOT HAPPEN: >>" + p._1 + "<< >>" + p._2.length + "<<"
            // p._2.foreach(f => println("-> " + f.name.value))
          } else {
            // println("DEBUG Moving " + p._2.head.name.value + " up to pkgbuf " + p._1)
            pkgbuf(p._2.head.name.value) = p._2.head
            // TODO will this work out with the foreach loop above???
            binbuf -= p._1
          }
        }
      })
      // another loop to collection and fill the collections buffer
      pkgs.foreach(pkg => {
        if (tlpkgs.contains(pkg._1)) {
          if (tlpkgs(pkg._1).category == "Collection") {
            val foo: immutable.Seq[String] = tlpkgs(pkg._1).depends
            colbuf(pkg._1) = ArrayBuffer[TLPackageDisplay]()
            // TODO we need to deal with packages that get removed!!!
            // for now just make sure we don't add them here!
            colbuf(pkg._1) ++= foo.filter(pkgbuf.contains(_)).map(pkgbuf(_))
          }
        } else if (pkg._1 == "root") {
          // do nothing
        } else {
          errorText += "Cannot find information for " + pkg._1
        }
      })
      // now we have all normal packages in pkgbuf, and its sub-packages in binbuf
      // we need to create TreeItems
      val viewkids: Seq[TreeItem[TLPackageDisplay]] =
        if (ViewByPkg.selected.value)
          view_pkgs_by_names(pkgbuf, binbuf)
        else
          view_pkgs_by_collections(pkgbuf, binbuf, colbuf)
      // println("DEBUG: leaving pkgs.onChange before runLater")
      Platform.runLater {
        packageTable.root = new TreeItem[TLPackageDisplay](new TLPackageDisplay("root", "0", "0", "", "0", "")) {
          expanded = true
          children = viewkids.sortBy(_.value.value.name.value)
        }
      }
    }
  })
  upds.onChange( (obs, chs) => {
    var doit = chs match {
      case ObservableMap.Add(k, v) => k.toString == "root"
      case ObservableMap.Replace(k, va, vr) => k.toString == "root"
      case ObservableMap.Remove(k, v) => k.toString == "root"
    }
    if (doit) {
      val infraAvailable = upds.keys.exists(_.startsWith("texlive.infra"))
      // only allow for updates of other packages when no infra update available
      val updatesAvailable = !infraAvailable && upds.keys.exists(p => !p.startsWith("texlive.infra") && !(p == "root"))
      val newroot = new TreeItem[TLUpdateDisplay](new TLUpdateDisplay("root", "", "", "", "", "")) {
        children = upds
          .filter(_._1 != "root")
          .map(p => new TreeItem[TLUpdateDisplay](p._2))
          .toArray
          .sortBy(_.value.value.name.value)
      }
      Platform.runLater {
        update_self_menu.disable = !infraAvailable
        update_all_menu.disable = !updatesAvailable
        updateTable.root = newroot
        if (infraAvailable) {
          texlive_infra_update_warning()
        }
      }
    }
  })

  def texlive_infra_update_warning(): Unit = {
    new Alert(AlertType.Warning) {
      initOwner(stage)
      title = "TeX Live Infrastructure Update Available"
      headerText = "Updates to the TeX Live Manager (Infrastructure) available."
      contentText = "Please use \"Update self\" from the Menu!"
    }.showAndWait()
  }

  def load_backups_update_bkps_view(): Unit = {
    val tmp = new Label("Loading backups, please wait ...")
    tmp.wrapText = true
    tmp.opacity = 0.4f
    tmp.font = new Font(30f)
    val prevph = backupTable.placeholder.value
    backupTable.placeholder = tmp
    tlmgr_send("restore --json", (status, lines) => {
      val jsonAst = lines.mkString("").parseJson
      val backups: Map[String, Map[String, TLBackupDisplay]] =
          jsonAst
            .convertTo[List[TLBackup]]
            .groupBy[String](_.name)
            .map(p => (p._1, p._2.map(q => (q.rev, new TLBackupDisplay(q.name, q.rev, q.date))).toMap))
      bkps.clear()
      bkps ++= backups
      trigger_update("bkps")
      backupTable.placeholder = prevph
    })
  }

  def update_pkgs_view(): Unit = {
    val newpkgs: Map[String, TLPackageDisplay] =
      tlpkgs
        .filter { p =>
          val searchTerm = searchEntry.text.value.toLowerCase
          p._1.toLowerCase.contains(searchTerm) ||
            p._2.shortdesc.getOrElse("").toLowerCase.contains(searchTerm)
        }
        .map { p =>
          (p._2.name,
            new TLPackageDisplay(
              p._2.name, p._2.lrev.toString, p._2.rrev.toString,
              p._2.shortdesc.getOrElse(""), "0", if (p._2.installed) "Installed" else "Not installed"
            )
          )
        }.toMap
    pkgs.clear()
    pkgs ++= newpkgs
    trigger_update("pkgs")
  }

  def load_tlpdb_update_pkgs_view():Unit = {
    val tmp = new Label("Loading database, please wait ...")
    tmp.wrapText = true
    tmp.opacity = 0.4f
    tmp.font = new Font(30f)
    val prevph = packageTable.placeholder.value
    packageTable.placeholder = tmp
    tlmgr_send("info --json", (status, lines) => {
      val jsonAst = lines.mkString("").parseJson
      tlpkgs.clear()
      tlpkgs ++= jsonAst.convertTo[List[TLPackage]].map { p => (p.name, p)}
      update_pkgs_view()
      packageTable.placeholder = prevph
    })
  }

  def parse_one_update_line(l: String): TLUpdateDisplay = {
    val fields = l.split("\t")
    val pkgname = fields(0)
    val status = fields(1) match {
      case "d" => "Removed on server"
      case "f" => "Forcibly removed"
      case "u" => "Update available"
      case "r" => "Local is newer"
      case "a" => "New on server"
      case "i" => "Not installed"
      case "I" => "Reinstall"
    }
    val localrev = fields(2)
    val serverrev = fields(3)
    val size = if (fields(1) == "d") "0" else humanReadableByteSize(fields(4).toLong)
    val runtime = fields(5)
    val esttot = fields(6)
    val tag = fields(7)
    val lctanv = fields(8)
    val rctanv = fields(9)
    val tlpkg: TLPackageDisplay = pkgs(pkgname)
    val shortdesc = tlpkg.shortdesc.value
    new TLUpdateDisplay(pkgname, status,
      localrev + {
        if (lctanv != "-") s" ($lctanv)" else ""
      },
      serverrev + {
        if (rctanv != "-") s" ($rctanv)" else ""
      },
      shortdesc, size)
  }

  def load_updates_update_upds_view(): Unit = {
    val tmp = new Label("Loading updates, please wait ...")
    tmp.wrapText = true
    tmp.opacity = 0.4f
    tmp.font = new Font(30f)
    val prevph = updateTable.placeholder.value
    updateTable.placeholder = tmp
    tlmgr_send("update --list", (status, lines) => {
      // println(s"DEBUG got updates length ${lines.length}")
      // println(s"DEBUG tlmgr last output = ${lines}")
      val newupds: Map[String, TLUpdateDisplay] = lines.filter { l =>
        l match {
          case u if u.startsWith("location-url") => false
          case u if u.startsWith("total-bytes") => false
          case u if u.startsWith("end-of-header") => false
          case u if u.startsWith("end-of-updates") => false
          case u => true
        }
      }.map { l =>
        val foo = parse_one_update_line(l)
        (foo.name.value, foo)
      }.toMap
      val infraAvailable = newupds.keys.exists(_.startsWith("texlive.infra"))
      upds.clear()
      if (infraAvailable) {
        upds ++= Seq( ("texlive.infra", newupds("texlive.infra") ) )
      } else {
        upds ++= newupds
      }
      trigger_update("upds")
      updateTable.placeholder = prevph
    })
  }

  def trigger_update(s:String): Unit = {
    // println("DEBUG: Triggering update of " + s)
    if (s == "pkgs") {
      pkgs("root") = new TLPackageDisplay("root", "0", "0", "", "0", "")
    } else if (s == "upds") {
      upds("root") = new TLUpdateDisplay("root", "", "", "", "", "")
    } else if (s == "bkps") {
      bkps("root") = Map[String, TLBackupDisplay](("0", new TLBackupDisplay("root", "0", "0")))
    }
  }

  def doListView(files: Seq[String], clickable: Boolean): scalafx.scene.Node = {
    if (files.length <= 5) {
      val vb = new VBox()
      vb.children = files.map { f =>
        val fields = f.split(" ")
        new Label(fields(0)) {
          if (clickable) {
            textFill = Color.Blue
            onMouseClicked = { me: MouseEvent => OsTools.openFile(tlmgr.tlroot + "/" + fields(0)) }
            cursor = Cursor.Hand
          }
        }
      }
      vb
    } else {
      val vb = new ListView[String] {}
      vb.minHeight = 150
      vb.prefHeight = 150
      vb.maxHeight = 200
      vb.vgrow = Priority.Always
      vb.orientation = Orientation.Vertical
      vb.cellFactory = { p => {
        val foo = new ListCell[String]
        foo.item.onChange { (_, _, str) => foo.text = str }
        if (clickable) {
          foo.textFill = Color.Blue
          foo.onMouseClicked = { me: MouseEvent => OsTools.openFile(tlmgr.tlroot + "/" + foo.text.value) }
          foo.cursor = Cursor.Hand
        }
        foo
      }
      }
      // vb.children = docFiles.map { f =>
      vb.items = ObservableBuffer(files.map { f =>
        val fields = f.split(" ")
        fields(0)
      })
      vb
    }
  }

  val mainMenu: Menu = new Menu("TLCockpit") {
    items = List(
      // temporarily move here as we disable the Help menu
      new MenuItem("About") {
        onAction = (ae) => callback_about()
      },
      new MenuItem("Exit") {
        onAction = (ae: ActionEvent) => callback_quit()
      })
  }
  val toolsMenu: Menu = new Menu("Tools") {
    items = List(
      new MenuItem("Update filename databases ...") {
        onAction = (ae) => {
          callback_run_external("mktexlsr")
          callback_run_external("mtxrun --generate")
        }
      },
      // too many lines are quickly output -> GUI becomes hanging until
      // all the callbacks are done - call fmtutil with unbuffered = false
      new MenuItem("Rebuild all formats ...") { onAction = (ae) => callback_run_external("fmtutil --sys --all", false) },
      new MenuItem("Update font map database ...") {
        onAction = (ae) => callback_run_external("updmap --sys")
      }
    )
  }
  val ViewByPkg = new RadioMenuItem("by package name") {
    onAction = (ae) => {
      searchEntry.text = ""
      update_pkgs_view()
    }
  }
  val ViewByCol = new RadioMenuItem("by collections")  {
    onAction = (ae) => {
      searchEntry.text = ""
      update_pkgs_view()
    }
  }
  ViewByPkg.selected = true
  ViewByCol.selected = false
  val pkgsMenu: Menu = new Menu("Packages") {
    val foo = new ToggleGroup
    foo.toggles = Seq(ViewByPkg, ViewByCol)
    items = List(ViewByPkg, ViewByCol)
  }
  var disable_auto_removal = false
  var disable_auto_install = false
  var enable_reinstall_forcible = false
  val updMenu: Menu = new Menu("Updates") {
    items = List(
      update_all_menu,
      update_self_menu,
      new SeparatorMenuItem,
      new CheckMenuItem("Disable auto removal") { onAction = (ae) => disable_auto_removal = selected.value },
      new CheckMenuItem("Disable auto install") { onAction = (ae) => disable_auto_install = selected.value },
      new CheckMenuItem("Reinstall forcibly removed") { onAction = (ae) => enable_reinstall_forcible = selected.value }
    )
  }


  def callback_general_options(): Unit = {
    tlmgr_send("option showall --json", (status, lines) => {
      val jsonAst = lines.mkString("").parseJson
      val tlpdopts: List[TLOption] = jsonAst.convertTo[List[TLOption]]
      Platform.runLater {
        val dg = new OptionsDialog(tlpdopts)
        dg.showAndWait() match {
          case Some(changedOpts) =>
            changedOpts.foreach(p => {
              // don't believe it or not, but \" does *NOT* work in Scala in
              // interpolated strings, and it seems there is no better way
              // than that one ...
              tlmgr_send(s"option ${p._1} ${'"'}${p._2}${'"'}", (_,_) => None)
            })
          case None =>
        }
      }
    })
  }


  def callback_paper(): Unit = {
    tlmgr_send("paper --json", (status, lines) => {
      val jsonAst = lines.mkString("").parseJson
      val paperconfs: Map[String, TLPaperConf] = jsonAst.convertTo[List[TLPaperConf]].map { p => (p.program, p) }.toMap
      val currentPapers: Map[String, String] = paperconfs.mapValues(p => p.options.head)
      Platform.runLater {
        val dg = new PaperDialog(paperconfs)
        dg.showAndWait() match {
          case Some(newPapers) =>
            // println(s"Got result ${newPapers}")
            // collect changed settings
            val changedPapers = newPapers.filter(p => currentPapers(p._1) != p._2)
            // println(s"Got changed papers ${changedPapers}")
            changedPapers.foreach(p => {
              tlmgr_send(s"paper ${p._1} paper ${p._2}", (_,_) => None)
            })
          case None =>
        }
      }
    })
  }

  val optionsMenu: Menu = new Menu("Options") {
    items = List(
      new MenuItem("General ...") { onAction = (ae) => callback_general_options() },
      new MenuItem("Paper ...") { onAction = (ae) => callback_paper() },
      /* new MenuItem("Platforms ...") { disable = true; onAction = (ae) => not_implemented_info() },
      new SeparatorMenuItem,
      new CheckMenuItem("Expert options") { disable = true },
      new CheckMenuItem("Enable debugging options") { disable = true },
      new CheckMenuItem("Disable auto-install of new packages") { disable = true },
      new CheckMenuItem("Disable auto-removal of server-deleted packages") { disable = true } */
    )
  }
  val statusMenu: Menu = new Menu("Status: Idle") {
    disable = true
  }
  val expertPane: TitledPane = new TitledPane {
    text = "Experts only"
    collapsible = true
    expanded = false
    content = new VBox {
      spacing = 10
      children = List(
        new HBox {
          spacing = 10
          alignment = Pos.CenterLeft
          children = List(
            new Label("tlmgr shell command:"),
            cmdline,
            new Button {
              text = "Go"
              onAction = (event: ActionEvent) => callback_run_cmdline()
            }
          )
        }
      )
    }
  }
  val updateTable: TreeTableView[TLUpdateDisplay] = {
    val colName = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Package"
      cellValueFactory = { _.value.value.value.name }
      prefWidth = 150
    }
    val colStatus = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Status"
      cellValueFactory = { _.value.value.value.status }
      prefWidth = 120
    }
    val colDesc = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Description"
      cellValueFactory = { _.value.value.value.shortdesc }
      prefWidth = 300
    }
    val colLRev = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Local rev"
      cellValueFactory = { _.value.value.value.lrev }
      prefWidth = 100
    }
    val colRRev = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Remote rev"
      cellValueFactory = { _.value.value.value.rrev }
      prefWidth = 100
    }
    val colSize = new TreeTableColumn[TLUpdateDisplay, String] {
      text = "Size"
      cellValueFactory = { _.value.value.value.size }
      prefWidth = 70
    }
    val table = new TreeTableView[TLUpdateDisplay](
      new TreeItem[TLUpdateDisplay](new TLUpdateDisplay("root","","","","","")) {
        expanded = false
      }) {
      columns ++= List(colName, colStatus, colDesc, colLRev, colRRev, colSize)
    }
    colDesc.prefWidth.bind(table.width - colName.width - colLRev.width - colRRev.width - colSize.width - colStatus. width - 15)
    table.prefHeight = 300
    table.vgrow = Priority.Always
    table.placeholder = new Label("No updates available")
    table.showRoot = false
    table.rowFactory = { _ =>
      val row = new TreeTableRow[TLUpdateDisplay] {}
      val infoMI = new MenuItem("Info") {
        onAction = (ae) => new PkgInfoDialog(row.item.value.name.value).showAndWait()
      }
      val updateMI = new MenuItem("Update") {
        onAction = (ae) => callback_update(row.item.value.name.value)
      }
      val installMI = new MenuItem("Install") {
        onAction = (ae) => callback_install(row.item.value.name.value)
      }
      val removeMI = new MenuItem("Remove") {
        onAction = (ae) => callback_remove(row.item.value.name.value)
      }
      val ctm = new ContextMenu(infoMI, updateMI, installMI, removeMI)
      row.item.onChange { (_,_,newTL) =>
        if (newTL != null) {
          if (newTL.status.value == "New on server") {
            installMI.disable = false
            removeMI.disable = true
            updateMI.disable = true
          } else if (newTL.status.value == "Removed on server") {
            installMI.disable = true
            removeMI.disable = false
            updateMI.disable = true
          } else {
            installMI.disable = true
            removeMI.disable = false
            updateMI.disable = false
          }
        }
      }
      row.contextMenu = ctm
      row
    }
    table
  }
  val packageTable: TreeTableView[TLPackageDisplay] = {
    val colName = new TreeTableColumn[TLPackageDisplay, String] {
      text = "Package"
      cellValueFactory = {  _.value.value.value.name }
      prefWidth = 150
    }
    val colDesc = new TreeTableColumn[TLPackageDisplay, String] {
      text = "Description"
      cellValueFactory = { _.value.value.value.shortdesc }
      prefWidth = 300
    }
    val colInst = new TreeTableColumn[TLPackageDisplay, String] {
      text = "Installed"
      cellValueFactory = { _.value.value.value.installed  }
      prefWidth = 100
    }
    val table = new TreeTableView[TLPackageDisplay](
      new TreeItem[TLPackageDisplay](new TLPackageDisplay("root","0","0","","0","")) {
        expanded = false
      }) {
      columns ++= List(colName, colDesc, colInst)
    }
    colDesc.prefWidth.bind(table.width - colInst.width - colName.width - 15)
    table.prefHeight = 300
    table.showRoot = false
    table.vgrow = Priority.Always
    table.rowFactory = { p =>
      val row = new TreeTableRow[TLPackageDisplay] {}
      val infoMI = new MenuItem("Info") {
        onAction = (ae) => new PkgInfoDialog(row.item.value.name.value).showAndWait()
      }
      val installMI = new MenuItem("Install") {
        onAction = (ae) => callback_install(row.item.value.name.value)
      }
      val removeMI = new MenuItem("Remove") {
        onAction = (ae) => callback_remove(row.item.value.name.value)
      }
      val ctm = new ContextMenu(infoMI, installMI, removeMI)
      row.item.onChange { (_,_,newTL) =>
        if (newTL != null) {
          val is_installed: Boolean = !(newTL.installed.value == "Not installed")
          installMI.disable = is_installed
          removeMI.disable = !is_installed
        }
      }
      row.contextMenu = ctm
      row
    }
    table
  }
  val backupTable: TreeTableView[TLBackupDisplay] = {
    val colName = new TreeTableColumn[TLBackupDisplay, String] {
      text = "Package"
      cellValueFactory = {  _.value.value.value.name }
      prefWidth = 150
    }
    val colRev = new TreeTableColumn[TLBackupDisplay, String] {
      text = "Revision"
      cellValueFactory = { _.value.value.value.rev }
      prefWidth = 100
    }
    val colDate = new TreeTableColumn[TLBackupDisplay, String] {
      text = "Date"
      cellValueFactory = { _.value.value.value.date }
      prefWidth = 300
    }
    val table = new TreeTableView[TLBackupDisplay](
      new TreeItem[TLBackupDisplay](new TLBackupDisplay("root","","")) {
        expanded = false
      }) {
      columns ++= List(colName, colRev, colDate)
    }
    colDate.prefWidth.bind(table.width - colRev.width - colName.width - 15)
    table.prefHeight = 300
    table.showRoot = false
    table.vgrow = Priority.Always
    table.rowFactory = { _ =>
      val row = new TreeTableRow[TLBackupDisplay] {}
      val ctm = new ContextMenu(
        new MenuItem("Info") {
          onAction = (ae) => new PkgInfoDialog(row.item.value.name.value).showAndWait()
        },
        new MenuItem("Restore") {
          onAction = (ae) => callback_restore(row.item.value.name.value, row.item.value.rev.value)
        }
      )
      row.contextMenu = ctm
      row
    }
    table
  }
  val searchEntry = new TextField()
  searchEntry.hgrow = Priority.Sometimes
  searchEntry.onKeyPressed = {
    (ae: KeyEvent) => if (ae.code == KeyCode.Enter) update_pkgs_view()
  }
  val searchBox = new HBox {
    children = Seq(
      new Label("Search:") {
        vgrow = Priority.Always
        alignmentInParent = Pos.CenterLeft
      },
      searchEntry,
      new Button("Go") {
        onAction = _ => update_pkgs_view()
      },
      new Button("Reset") {
        onAction = _ => {
          searchEntry.text = ""
          update_pkgs_view()
        }
      }
    )
    alignment = Pos.Center
    padding = Insets(10)
    spacing = 10
  }
  val pkgsContainer = new VBox {
    children = Seq(packageTable,searchBox)
  }

  val pkgstabs: TabPane = new TabPane {
    minWidth = 400
    vgrow = Priority.Always
    tabs = Seq(
      new Tab {
        text = "Packages"
        closable = false
        content = pkgsContainer
      },
      new Tab {
        text = "Updates"
        closable = false
        content = updateTable
      },
      new Tab {
        text = "Backups"
        closable = false
        content = backupTable
      }
    )
  }
  // val spacerMenu: Menu = new Menu("                        ")
  // spacerMenu.disable = true
  // spacerMenu.hgrow = Priority.Always
  val menuBar: MenuBar = new MenuBar {
    useSystemMenuBar = true
    // menus.addAll(mainMenu, optionsMenu, helpMenu)
    menus.addAll(mainMenu, pkgsMenu, toolsMenu, optionsMenu, statusMenu)
  }
  pkgstabs.selectionModel().selectedItem.onChange(
    (a,b,c) => {
      if (a.value.text() == "Backups") {
        if (backupTable.root.value.children.length == 0)
          load_backups_update_bkps_view()
        menuBar.menus = Seq(mainMenu, toolsMenu, optionsMenu, statusMenu)
      } else if (a.value.text() == "Updates") {
        // only update if not done already
        if (updateTable.root.value.children.length == 0)
          load_updates_update_upds_view()
        menuBar.menus = Seq(mainMenu, updMenu, toolsMenu, optionsMenu, statusMenu)
      } else if (a.value.text() == "Packages") {
        menuBar.menus = Seq(mainMenu, pkgsMenu, toolsMenu, optionsMenu, statusMenu)
      }
    }
  )


  stage = new PrimaryStage {
    title = "TLCockpit"
    scene = new Scene {
      root = {
        // val topBox = new HBox {
        //   children = List(menuBar, statusLabel)
        // }
        // topBox.hgrow = Priority.Always
        // topBox.maxWidth = Double.MaxValue
        val topBox = menuBar
        val centerBox = new VBox {
          padding = Insets(10)
          children = List(pkgstabs, expertPane, outerrpane)
        }
        new BorderPane {
          // padding = Insets(20)
          top = topBox
          // left = leftBox
          center = centerBox
          // bottom = bottomBox
        }
      }
    }
    icons.add(iconImage)
  }

  stage.onCloseRequest = (e: Event) => callback_quit()

  stage.width = 800


  var currentPromise = Promise[(String,Array[String])]()
  val pendingJobs = scala.collection.mutable.Queue[(String,(String, Array[String]) => Unit)]()

  def initialize_tlmgr(): TlmgrProcess = {
    tlmgrBusy.value = true
    val tt = new TlmgrProcess(
      (s: String) => outputLine.put(s),
      (s: String) => errorLine.put(s)
    )
    /* val tlmgrMonitor = Future {
      while (true) {
        if (!tlmgr.isAlive) {
          println("TLMGR HAS DIED!!!!")
          // Platform.exit()
          // sys.exit(1)
        }
        Thread.sleep(5000)
      }
    }*/
    val stdoutFuture = Future {
      val tlmgrOutput = ArrayBuffer[String]()
      var tlmgrStatus = ""
      var alive = true
      while (alive) {
        val s = outputLine.take
        if (s == null) {
          alive = false
          // println("GOT NULL from outputline tlmgr dead???")
        } else {
          //println(s"DEBUG: got " + s)
          if (s == "OK") {
            tlmgrStatus = s
          } else if (s == "ERROR") {
            tlmgrStatus = s
          } else if (s == "tlmgr> ") {
            // println("DEBUG: fulfilling current promise!")
            currentPromise.success((tlmgrStatus, tlmgrOutput.toArray))
            tlmgrStatus = ""
            tlmgrOutput.clear()
            tlmgrBusy.value = false
            if (pendingJobs.nonEmpty) {
              val nextCmd = pendingJobs.dequeue()
              tlmgr_run_one_cmd(nextCmd._1, nextCmd._2)
            }
          } else {
            tlmgrOutput += s
            stdoutLineUpdateFunc(s)
          }
        }
      }
    }
    tlmgrBusy.onChange({ Platform.runLater{ statusMenu.text = "Status: " + (if (tlmgrBusy.value) "Busy" else "Idle") }})
    stdoutFuture.onComplete {
      case Success(value) =>
        // println(s"tlmgr stdout reader terminated: ${value}")
        Platform.runLater {
          outerrpane.expanded = true
          outerrtabs.selectionModel().select(1)
          errorfield.scrollTop = Double.MaxValue
          logfield.scrollTop = Double.MaxValue
          new Alert(AlertType.Error) {
            initOwner(stage)
            title = "TeX Live Manager tlmgr has terminated"
            headerText = "TeX Live Manager tlmgr has terminated - we cannot continue"
            contentText = "Please inspect the debug output in the main window lower pane!"
          }.showAndWait()
          // Platform.exit()
          // sys.exit(1)
        }
      case Failure(e) =>
        errorText += "lineUpdateFunc(stdout) thread got interrupted -- probably old tlmgr, ignoring it!"
        //e.printStackTrace
    }
    val stderrFuture = Future {
      var alive = true
      while (alive) {
        val s = errorLine.take
        if (s == null)
          alive = false
        else
          stderrLineUpdateFunc(s)
      }
    }
    stderrFuture.onComplete {
      case Success(value) => // println(s"tlmgr stderr reader terminated: ${value}")
      case Failure(e) =>
        errorText += "lineUpdateFunc(stderr) thread got interrupted -- probably old tlmgr, ignoring it!"
      //e.printStackTrace
    }
    tt
  }

  def tlmgr_run_one_cmd(s: String, onCompleteFunc: (String, Array[String]) => Unit): Unit = {
    currentPromise = Promise[(String, Array[String])]()
    tlmgrBusy.value = true
    currentPromise.future.onComplete {
      case Success((a, b)) =>
        // println("DEBUG current future completed!")
        Platform.runLater {
          onCompleteFunc(a, b)
        }
      case Failure(ex) =>
        errorText += "Running a tlmgr command did not succeed: " + ex.getMessage
    }
    // println(s"DEBUG sending ${s}")
    tlmgr.send_command(s)
  }

  def tlmgr_send(s: String, onCompleteFunc: (String, Array[String]) => Unit): Unit = {
    // logText.clear()
    outputText.clear()
    outerrpane.expanded = false
    if (!currentPromise.isCompleted) {
      // println(s"DEBUG tlmgr busy, put onto pending jobs: $s")
      pendingJobs += ((s, onCompleteFunc))
    } else {
      tlmgr_run_one_cmd(s, onCompleteFunc)
    }
  }

  def reinitialize_tlmgr(): Unit = {
    tlmgr.cleanup()
    // Thread.sleep(1000)
    tlmgr = initialize_tlmgr()
    tlmgr_post_init()
    pkgstabs.getSelectionModel().select(0)
  }

  def tlmgr_post_init():Unit = {
    if (!tlmgr.start_process()) {
      println("Cannot start tlmgr process, terminating!")
      Platform.exit()
      sys.exit(1)
    }

    // check for tlmgr revision
    tlmgr_send("version", (status,output) => {
      output.foreach ( l => {
        if (l.startsWith("revision ")) {
          val tlmgrRev = l.stripPrefix("revision ")
          if (tlmgrRev == "unknown") {
            println("Unknown tlmgr revision, assuming git/svn version")
            logText += "Unknown tlmgr revision, assuming git/svn version"
          } else {
            if (tlmgrRev.toInt < 45838) {
              new Alert(AlertType.Error) {
                initOwner(stage)
                title = "TeX Live Manager tlmgr is too old"
                headerText = "TeX Live Manager tlmgr is too old"
                contentText = "Please update from the command line\nusing 'tlmgr update --self'\nTerminating!"
              }.showAndWait()
              callback_quit()
            }
          }
        }
      })
      pkgs.clear()
      upds.clear()
      bkps.clear()
      load_tlpdb_update_pkgs_view()
    })
  }

  def defaultStdoutLineUpdateFunc(l: String) : Unit = { } // println(s"DEBUG: got ==$l== from tlmgr") }
  def defaultStderrLineUpdateFunc(l: String) : Unit = { Platform.runLater { logText.append(l) } }

  var stdoutLineUpdateFunc: String => Unit = defaultStdoutLineUpdateFunc
  var stderrLineUpdateFunc: String => Unit = defaultStderrLineUpdateFunc
  var tlmgr = initialize_tlmgr()
  tlmgr_post_init()
}  // object ApplicationMain

// vim:set tabstop=2 expandtab : //
