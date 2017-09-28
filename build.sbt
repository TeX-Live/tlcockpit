name := "tlcockpit"

version := "0.3"

scalaVersion := "2.12.3"

libraryDependencies += "org.scalafx" % "scalafx_2.12" % "8.0.102-R11"

// unmanagedJars in Compile += Attributed.blank(file(System.getenv("JAVA_HOME") + "/jre/lib/ext/jfxrt.jar"))
// unmanagedJars in Compile += Attributed.blank(file("/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/ext/jfxrt.jar"))

mainClass in assembly := Some("TLCockpit.ApplicationMain")

assemblyJarName in assembly := "tlcockpit.jar"
assemblyOutputPath in assembly := file("jar/tlcockpit.jar")


// for scalafx
fork := true
