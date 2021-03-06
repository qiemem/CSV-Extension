scalaVersion := "2.9.2"

scalaSource in Compile := baseDirectory.value / "src" / "main"

scalaSource in Test := baseDirectory.value / "src" / "test"

javaSource in Compile := baseDirectory.value / "src" / "main"

javaSource in Test := baseDirectory.value / "src" / "test"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Xfatal-warnings",
                      "-encoding", "us-ascii")

retrieveManaged := true

libraryDependencies ++= Seq(
  "org.nlogo" % "NetLogo" % "5.1.0" from
    "http://ccl.northwestern.edu/netlogo/5.1.0/NetLogo.jar",
    "com.github.tototoshi" %% "scala-csv" % "1.0.0",
    "org.apache.commons" % "commons-csv" % "1.0"
)

libraryDependencies ++= Seq(
  "org.nlogo" % "NetLogo-tests" % "5.1.0" % "test" from
    "http://ccl.northwestern.edu/netlogo/5.1.0/NetLogo-tests.jar",
  "org.scalatest" %% "scalatest" % "1.8" % "test",
  "org.picocontainer" % "picocontainer" % "2.13.6" % "test",
  "asm" % "asm-all" % "3.3.1" % "test"
)

artifactName := { (_, _, _) => "csv.jar" }

packageOptions +=
  Package.ManifestAttributes(
    ("Extension-Name", "csv"),
    ("Class-Manager", "org.nlogo.extensions.csv.CSVExtension"),
    ("NetLogo-Extension-API-Version", "5.0"))

packageBin in Compile := {
  val jar = (packageBin in Compile).value
  val classpath = (dependencyClasspath in Runtime).value
  val base = baseDirectory.value
  val s = streams.value
  IO.copyFile(jar, base / "csv.jar")
  def pack200(name: String) {
    Process(sys.env("JAVA_HOME") + "/bin/pack200 " +
            "--modification-time=latest --effort=9 --strip-debug " +
            "--no-keep-file-order --unknown-attribute=strip " +
            name + ".pack.gz " + name).!!
  }
  pack200("csv.jar")
  val libraryJarPaths =
    classpath.files.filter{path =>
      path.getName.endsWith(".jar") &&
      !path.getName.startsWith("scala-library")}
  for(path <- libraryJarPaths) {
    IO.copyFile(path, base / path.getName)
    pack200(path.getName)
  }
  if(Process("git diff --quiet --exit-code HEAD").! == 0) {
    // copy everything thing we need for distribution in
    // a temporary "csv" directory, which we will then zip
    // before deleting it.
    IO.createDirectory(base / "csv")
    val zipExtras =
      (libraryJarPaths.map(_.getName) :+ "csv.jar")
        .filterNot(_ contains "NetLogo")
        .flatMap{ jar => Seq(jar, jar + ".pack.gz") }
    for(extra <- zipExtras)
      IO.copyFile(base / extra, base / "csv" / extra)
    for (dir <- Seq("alternate-netlogolite", "demo"))
      IO.copyDirectory(base / dir, base / "csv" / dir)
    Process("zip -r csv.zip csv").!!
    IO.delete(base / "csv")
  }
  else {
    s.log.warn("working tree not clean; no zip archive made")
    IO.delete(base / "csv.zip")
  }
  jar
}

test in Test := {
  val _ = (packageBin in Compile).value
  (test in Test).value
}

cleanFiles ++= {
  val base = baseDirectory.value
  Seq(base / "csv.jar",
      base / "csv.jar.pack.gz",
      base / "csv.zip")
}
