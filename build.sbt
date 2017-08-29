name := "tlcockpit"

version := "0.1"

scalaVersion := "2.12.3"

libraryDependencies += "org.scalafx" % "scalafx_2.12" % "8.0.102-R11"

unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))
unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/jfxswt.jar"))

mainClass in assembly := Some("TLCockpit.ApplicationMain")

// for scalafx
fork := true
