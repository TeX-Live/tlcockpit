name := "tlmgr-scala"

version := "0.1"

scalaVersion := "2.12.3"

// libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "2.0.0-M2"
libraryDependencies += "org.scalafx" % "scalafx_2.12" % "8.0.102-R11"

//libraryDependencies ++= Seq(
//  "org.scala-lang.modules" % "scala-swing_2.11" % "2.0.0"
//)
//	"com.michaelpollmeier" %% "gremlin-scala" % "3.2.4.15",
//	"org.apache.tinkerpop" % "neo4j-gremlin" % "3.2.4" exclude("com.github.jeremyh", "jBCrypt"), // travis can't find jBCrypt...
//	"org.neo4j" % "neo4j-tinkerpop-api-impl" % "0.4-3.0.3",
//	"org.slf4j" % "slf4j-nop" % "1.7.25" % Test,
//	"org.scalatest" %% "scalatest" % "3.0.3" % Test
//)
// envVars := Map("GTK_THEME" -> "Adwaita:light")
//fork in Test := true
fork := true
//resolvers += Resolver.mavenLocal
