package TLCockpit

import TLCockpit.ApplicationMain.{not_implemented_info, stage}
import TeXLive.{OsTools, TLOption, TLPaperConf}

import scalafx.Includes._
import scalafx.collections.ObservableBuffer
import scalafx.event.ActionEvent
import scalafx.geometry.{HPos, Insets}
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

class OptionsDialog(opts: List[TLOption]) {

  case class Result(selected: Map[String,String])

  val dialog = new Dialog[Result]() {
    initOwner(stage)
    title = s"General options"
    headerText = s"General options"
    resizable = true
  }
  dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
  val grid = new GridPane() {
    hgap = 10
    vgap = 10
    padding = Insets(20)
  }

  var crow = 0
  val optsMap = opts.map(p => (p.name, p)).toMap
  val orderOpts = Seq("location", "create_formats", "install_srcfiles", "install_docfiles",
                      "backupdir", "autobackup") ++ {
      if (OsTools.isWindows) Seq("desktop_integration", "file_assocs", "w32_multi_user")
      else Seq("sys_bin", "sys_info", "sys_man")
    }
  val changedValues = scala.collection.mutable.Map[String,String]()
  val nodes = orderOpts.map( nm => {
    val tlopt = optsMap(nm) // this is dangerous in case we change the names in TLPDB!
    grid.add(new Label(tlopt.description), 0, crow)
    val value = tlopt.value.getOrElse(tlopt.default)
    val nd: Node = if (tlopt.format.startsWith("b"))
      new CheckBox() {
        selected = if (value == "1") true else false
        selected.onChange( (_,_,newVal) => { changedValues(nm) = if (newVal) "1" else "0" })
      }
    else if (tlopt.format.startsWith("n")) {
      // parse additional specs
      if (tlopt.format.startsWith("n:")) {
        var nrSpec = tlopt.format.substring(2)
        var splits: Array[String] = nrSpec.split("""\.\.""")
        val start: Int = splits(0).toInt
        val end: Option[Int] = if (splits.length > 1) Some(splits(1).toInt) else None
        new Spinner[Int](start, end.getOrElse(50), value.toInt) {
          value.onChange( (_,_,newVal) => { changedValues(nm) = newVal.toString })
          if (nm == "autobackup")
            tooltip = new Tooltip("-1 ... arbitrary many backups\n0 ... no backups\notherwise number of backups")
        }
      } else {
        new Spinner[Int](-50, 50, value.toInt) {
          value.onChange( (_,_,newVal) => { changedValues(nm) = newVal.toString })
        }
      }
    } else if (tlopt.format == "p")
      new TextField() {
        text = value
        text.onChange( (_,_,newVal) => { changedValues(nm) = newVal })
      }
    else if (tlopt.format == "u") {
      val locs = value.split(' ')
      val locsmap = if (locs.length == 1) Map[String,String](("main", value))
      else locs.map(p => {
        val uu = p.split('#')
        (uu(1), uu(0))
      }).toMap
      val buttonStr = if (locs.length == 1) value else "Multiple repository"
      new Button(buttonStr) {
        onAction = (event: ActionEvent) => {
          val dg = new LocationDialog(locsmap)
          dg.showAndWait() match {
            case Some(newlocs) =>
              if (newlocs.toList.length == 1) {
                changedValues(nm) = newlocs("main")
                this.text = newlocs("main")
              } else {
                changedValues(nm) = newlocs.map(p => p._2 + "#" + p._1).mkString(" ")
                this.text = "Multiple repository"
              }
            case None =>
          }
        }
      }
    } else
      new Label("Unknown setting")

    grid.add(nd, 1, crow)
    crow += 1
    (tlopt.name, nd)
  }).toMap

  grid.columnConstraints = Seq(new ColumnConstraints(300, 300, 500), new ColumnConstraints(200, 200, 5000, Priority.Always, new HPos(HPos.Left), true))
  dialog.dialogPane().content = grid
  dialog.width = 500
  dialog.height = 1500

  dialog.resultConverter = dialogButton =>
    if (dialogButton == ButtonType.OK) {
      // TODO we could check that the values actually have changed
      Result(changedValues.toMap)
    } else
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
