// TLCockpit
// Copyright 2017-2018 Norbert Preining
// Licensed according to GPLv3+
//
// Front end for tlmgr

package TLCockpit

import TLCockpit.ApplicationMain.stage
import TeXLive.{OsTools, TLOption}
import com.typesafe.scalalogging.LazyLogging

import scalafx.Includes._
import scalafx.event.ActionEvent
import scalafx.geometry.{HPos, Insets}
import scalafx.scene.Node
import scalafx.scene.control._
import scalafx.scene.layout._

class LocationDialog(locs: Map[String,String]) extends LazyLogging {

  case class Result(selected: Map[String, String])

  val dialog = new Dialog[Result]() {
    initOwner(stage)
    title = s"Default package repositories"
    headerText = s"Default package repositories"
    resizable = true
  }
  dialog.dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)
  val grid = new GridPane() {
    hgap = 10
    vgap = 10
    padding = Insets(20)
  }

  val newlocs = scala.collection.mutable.ArrayBuffer[(TextField, TextField)]()

  var crow = 0

  val mirrorlocs: Map[String, Map[String, Seq[String]]] = TLCockpit.ApplicationMain.parse_ctan_mirrors(TLCockpit.ApplicationMain.tlmgr.tlroot)

  def do_one(tag: String, url: String): Unit = {
    val tagNode = new TextField() {
      text = tag
    }
    tagNode.disable = (tag == "main")
    val urlNode = new TextField() {
      text = url
    }
    grid.add(tagNode, 0, crow)
    grid.add(urlNode, 1, crow)
    newlocs += ((tagNode, urlNode))
    if (tag == "main") {
      grid.add(new MenuButton("Choose Mirror") {
        items = mirrorlocs.toSeq.sortWith(_._1 < _._1)
          .map(continent_pair => new Menu(continent_pair._1) {
            items = continent_pair._2.toSeq.sortWith(_._1 < _._1)
              .map(country_pair => new Menu(country_pair._1) {
                items = country_pair._2.sortWith(_ < _).map(mirror => new MenuItem(mirror) {
                  onAction = (ae) => urlNode.text = mirror
                })
              })
          })
      }, 2, crow)
    } else {
      grid.add(new Button("Delete") {
        onAction = _ => {
          tagNode.disable = true
          urlNode.disable = true
          this.disable = true
        }
      }, 2, crow)
    }
    crow += 1
  }

  do_one("main", locs("main"))
  locs.filter(_._1 != "main").foreach(p => do_one(p._1, p._2))

  val addButton = new Button("Add")
  addButton.onAction = _ => {
    val tagNode = new TextField()
    val urlNode = new TextField()
    newlocs += ((tagNode, urlNode))
    logger.trace(s"current row = ${crow}")
    grid.children.remove(addButton)
    grid.add(tagNode, 0, crow)
    grid.add(urlNode, 1, crow)
    grid.add(new Button("Delete") {
      onAction = _ => {
        tagNode.disable = true
        urlNode.disable = true
        this.disable = true
      }
    }, 2, crow)
    crow += 1
    logger.trace(s"adding addButton at row ${crow}")
    grid.add(addButton, 2, crow)
    dialog.dialogPane.value.scene.value.window.value.sizeToScene()
  }
  grid.add(addButton, 2, crow)

  // val changedValues = scala.collection.mutable.Map[String,String]()


  grid.columnConstraints = Seq(new ColumnConstraints(100, 100, 200), new ColumnConstraints(250, 250, 5000, Priority.Always, new HPos(HPos.Left), true))
  dialog.dialogPane().content = grid
  dialog.width = 500
  dialog.height = 1500

  dialog.resultConverter = dialogButton =>
    if (dialogButton == ButtonType.OK) {
      val newlocsfiltered =
        newlocs.filter(p => (p._1.text.value + p._2.text.value).trim != "")
               .filter(p => !p._2.disabled.value)
               .map(p => (if (p._1.text.value == "") p._2.text.value else p._1.text.value, p._2.text.value))
               .toMap
      Result(newlocsfiltered)
    } else
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
