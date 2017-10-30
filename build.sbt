name := "tlcockpit"

version := "0.6json-dev"

scalaVersion := "2.12.3"

libraryDependencies += "org.scalafx" % "scalafx_2.12" % "8.0.102-R11"
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"

mainClass in assembly := Some("TLCockpit.ApplicationMain")

assemblyJarName in assembly := "tlcockpit.jar"
assemblyOutputPath in assembly := file("jar/tlcockpit.jar")


// for scalafx
fork := true
