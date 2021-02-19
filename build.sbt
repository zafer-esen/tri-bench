
lazy val commonSettings = Seq(
    name := "tri-bench",
    organization := "uuverifiers",
    version := "0.1",
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12", "2.12.8")
//    publishTo := Some(Resolver.file("file",  new File( "/home/wv/public_html/maven/" )) )
)

// Actual project

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
//  mainClass in Compile := Some("tricera.Main"),
  //
  scalacOptions in Compile ++=
    List("-feature",
         "-language:implicitConversions,postfixOps,reflectiveCalls"),
  scalacOptions += (scalaVersion map { sv => sv match {
                                        case "2.11.12" => "-optimise"
                                        case "2.12.8" => "-opt:_"
                                      }}).value,
  resolvers += ("uuverifiers" at "http://logicrunch.research.it.uu.se/maven/").withAllowInsecureProtocol(true),
  libraryDependencies += "uuverifiers" %% "tricera" % "0.2",
  libraryDependencies += "com.github.dwickern" %% "scala-nameof" % "3.0.0" % "provided"

)
  //
