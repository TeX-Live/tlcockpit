name := "tlcockpit"

version := "1.0"

scalaVersion := "2.12.7"

libraryDependencies += "org.scalafx" % "scalafx_2.12" % "8.0.181-R13"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.6.0-M3"
libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.6.0-M3"

mainClass in assembly := Some("TLCockpit.ApplicationMain")

assemblyJarName in assembly := "tlcockpit.jar"
assemblyOutputPath in assembly := file("jar/tlcockpit.jar")


// for scalafx
fork := true
