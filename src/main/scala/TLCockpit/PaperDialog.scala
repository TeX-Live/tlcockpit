// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

import TLCockpit.ApplicationMain.stage
import TeXLive.TLPaperConf
import com.typesafe.scalalogging.LazyLogging

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{HPos, Insets}
import scalafx.scene.control._
import scalafx.scene.layout._

class PaperDialog(paperconf: Map[String, TLPaperConf]) extends LazyLogging   {

  case class Result(selected: Map[String,String])

  val dialog = new Dialog[Result]() {
    initOwner(stage)
    title = s"Paper configuration"
    headerText = s"Paper configuration"
    resizable = true
  }
  dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
  val grid = new GridPane() {
    hgap = 10
    vgap = 10
    padding = Insets(20)
  }

  var crow = 0

  def update_all_to(str: String): Unit = {
    cbseq.foreach(f => f._2.value = str)
  }
  grid.add(new Label("Set all to:"), 0, crow)
  grid.add(new HBox(20) {
    children = List(
      new Button("a4") { onAction = _ => update_all_to("a4") },
      new Button("letter") { onAction = _ => update_all_to("letter")}
    )}, 1, crow)
  crow += 1
  def do_one(k: String, v: ChoiceBox[String], row: Int): Int = {
    grid.add(new Label(k), 0, row)
    grid.add(v, 1, row)
    row + 1
  }

  val cbseq: Map[String, ChoiceBox[String]] = paperconf.map { p =>
    val prog: String = p._1
    val opts: TLPaperConf = p._2
    val paperopts: Seq[String] = opts.options
    val cb = new ChoiceBox[String](ObservableBuffer(paperopts.sorted))
    cb.value = paperopts.head
    crow = do_one(prog,cb, crow)
    (prog, cb)
  }

  grid.columnConstraints = Seq(new ColumnConstraints(100, 100, 150), new ColumnConstraints(150, 150, 5000, Priority.Always, HPos.LEFT, true))
  dialog.dialogPane().content = grid
  dialog.width = 300
  dialog.height = 1500

  dialog.resultConverter = dialogButton =>
    if (dialogButton == ButtonType.OK)
      Result(cbseq.mapValues(_.value.value))
    else
      null

  def showAndWait(): Option[Map[String,String]] = {
    val result = dialog.showAndWait()

    result match {
      case Some(Result(foo)) =>
        logger.debug("Got result " + foo)
        Some(foo)
      case Some(foo) =>
        logger.debug("Got strange result " + foo)
        None
      case None =>
        None
    }
  }

}
