package TeXLive

import java.util.Optional
import javafx.scene.Node

import TLCockpit.ApplicationMain.{stage, tlmgr, tlpkgs}
import TLCockpit.Utils._

import scala.collection.{convert, immutable}
import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.geometry.{HPos, Insets, Orientation}
import scalafx.scene.Cursor
import scalafx.scene.control._
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout._
import scalafx.scene.paint.Color

class PaperDialog(paperconf: Map[String, TLPaperConf])  {

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

  // TODO buttons for all to a4 and all to letter
  val cbseq: Map[String, ChoiceBox[String]] = paperconf.map { p =>
    val prog: String = p._1
    val opts: TLPaperConf = p._2
    val paperopts: Seq[String] = opts.options
    val cb = new ChoiceBox[String](ObservableBuffer(paperopts.sorted))
    cb.value = paperopts.head
    crow = do_one(prog,cb, crow)
    (prog, cb)
  }

  grid.columnConstraints = Seq(new ColumnConstraints(100, 200, 200), new ColumnConstraints(100, 400, 5000, Priority.Always, new HPos(HPos.Left), true))
  dialog.dialogPane().content = grid
  dialog.width = 600
  dialog.height = 1500
  // dialog

  dialog.resultConverter = dialogButton =>
    if (dialogButton == ButtonType.OK)
      Result(cbseq.mapValues(_.value.value))
    else
      null

  def showAndWait(): Option[Map[String,String]] = {
    val result = dialog.showAndWait()

    result match {
      case Some(Result(foo)) =>
        // println("Got resutl " + foo)
        Some(foo)
      case Some(foo) =>
        // println("Got strange result " + foo)
        None
      case None =>
        None
    }
  }

}
