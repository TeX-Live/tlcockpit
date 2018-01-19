// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

import TLCockpit.ApplicationMain.{stage, tlmgr, tlpkgs}
import TLCockpit.Utils._
import TeXLive.{OsTools, TLPackage}

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{HPos, Insets, Orientation}
import scalafx.scene.Cursor
import scalafx.scene.layout.{ColumnConstraints, GridPane, Priority, VBox}
import scalafx.scene.control._
import scalafx.scene.input.MouseEvent
import scalafx.scene.paint.Color

class PkgInfoDialog(tlp: TLPackage) extends Dialog {
  val dialog = new Dialog() {
    initOwner(stage)
    title = s"Package Information for $pkg"
    headerText = s"Package Information for $pkg"
    resizable = true
  }
  dialog.dialogPane().buttonTypes = Seq(ButtonType.OK)
  val isInstalled = tlp.installed
  val grid = new GridPane() {
    hgap = 10
    vgap = 10
    padding = Insets(20)
  }

  def do_one(k: String, v: String, row: Int): Int = {
    grid.add(new Label(k), 0, row)
    grid.add(new Label(v) {
      wrapText = true
    }, 1, row)
    row + 1
  }

  var crow = 0
  val pkg = tlp.name
  crow = do_one("package", pkg, crow)
  crow = do_one("category", tlp.category, crow)
  crow = do_one("shortdesc", tlp.shortdesc.getOrElse(""), crow)
  crow = do_one("longdesc", tlp.longdesc.getOrElse(""), crow)
  crow = do_one("installed", if (tlp.installed) "Yes" else "No", crow)
  crow = do_one("available", if (tlp.available) "Yes" else "No", crow)
  if (tlp.installed)
    crow = do_one("local revision", tlp.lrev.toString, crow)
  if (tlp.available)
    crow = do_one("remote revision", tlp.rrev.toString, crow)
  val binsizesum = tlp.binsize.values.toList.sum * TeXLive.tlBlockSize
  val binsizestr = if (binsizesum > 0) "bin " + humanReadableByteSize(binsizesum) + " " else ""
  val runsizestr = if (tlp.runsize > 0) "run " + humanReadableByteSize(tlp.runsize) + " " else ""
  val srcsizestr = if (tlp.srcsize > 0) "src " + humanReadableByteSize(tlp.srcsize) + " " else ""
  val docsizestr = if (tlp.docsize > 0) "doc " + humanReadableByteSize(tlp.docsize) + " " else ""
  crow = do_one("sizes", runsizestr + docsizestr + binsizestr + srcsizestr, crow)
  val catdata = tlp.cataloguedata
  if (catdata.version.isDefined)
    crow = do_one("cat-version", catdata.version.get, crow)
  if (catdata.date.isDefined)
    crow = do_one("cat-date", catdata.date.get, crow)
  if (catdata.license.isDefined)
    crow = do_one("cat-license", catdata.license.get, crow)
  if (catdata.topics.isDefined)
    crow = do_one("cat-topics", catdata.topics.get, crow)
  if (catdata.related.isDefined)
    crow = do_one("cat-related", catdata.related.get, crow)
  // add files section
  //println(tlpkgs(pkg))
  val docFiles = tlp.docfiles
  if (docFiles.nonEmpty) {
    grid.add(new Label("doc files"), 0, crow)
    grid.add(doListView(docFiles.map(s => s.file.replaceFirst("RELOC", "texmf-dist")), isInstalled), 1, crow)
    crow += 1
  }
  val runFiles = tlp.runfiles
  if (runFiles.nonEmpty) {
    grid.add(new Label("run files"), 0, crow)
    grid.add(doListView(runFiles.map(s => s.replaceFirst("RELOC", "texmf-dist")), false), 1, crow)
    crow += 1
  }
  val srcFiles = tlp.srcfiles
  if (srcFiles.nonEmpty) {
    grid.add(new Label("src files"), 0, crow)
    grid.add(doListView(srcFiles.map(s => s.replaceFirst("RELOC", "texmf-dist")), false), 1, crow)
    crow += 1
  }
  val binFiles = tlp.binfiles
  if (binFiles.nonEmpty) {
    grid.add(new Label("bin files"), 0, crow)
    grid.add(doListView(binFiles.flatMap(_._2).toSeq.map(s => s.replaceFirst("RELOC", "texmf-dist")), false), 1, crow)
    crow += 1
  }
  grid.columnConstraints = Seq(new ColumnConstraints(100, 200, 200), new ColumnConstraints(100, 400, 5000, Priority.Always, new HPos(HPos.Left), true))
  dialog.dialogPane().content = grid
  dialog.width = 600
  dialog.height = 1500
  // dialog

  def showAndWait(): Unit = this.dialog.showAndWait()

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
      vb.minHeight = 100
      vb.prefHeight = 100
      vb.maxHeight = 200
      vb.vgrow = Priority.Always
      vb.orientation = Orientation.Vertical
      vb.cellFactory = { p => {
        val foo = new ListCell[String]
        // ensure tighter spacing similar to the other entires
        foo.padding = Insets(0)
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

}
