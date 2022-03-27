ThisBuild / scalaVersion := "3.1.1"
bspEnabled               := false

val myScalacOptions = Seq(
  "-source",
  "3.0",
  "-rewrite",
  // "-new-syntax",
  "-indent",
  "-language:implicitConversions",
  "-deprecation",     // emit warning and location for usages of deprecated APIs
  "-explain",         // explain errors in more detail
  "-explain-types",   // explain type errors in more detail
  "-feature",         // emit warning and location for usages of features that should be imported explicitly
  // "-indent", // allow significant indentation.
  "-print-lines",     // show source code line numbers.
  "-unchecked",       // enable additional warnings where generated code depends on assumptions
  "-Ykind-projector", // allow `*` as wildcard to be compatible with kind projector
  "-Xfatal-warnings", // fail the compilation if there are any warnings
  "-Xmigration"       // warn about constructs whose behavior may have changed since version
)
lazy val root       = project
  .in(file("."))
  .aggregate(commonUtil, ibkr)
  .settings(
    name          := "ibkr-tws-root",
    version       := "0.2.0",
    scalacOptions := myScalacOptions
  )

lazy val commonUtil = project
  .in(file("./modules/common"))
  .settings(
    name    := "odenzo-common-lib",
    libraryDependencies ++= Libs.all,
    version := "0.1.0"
  )

lazy val ibkr = project
  .in(file("./modules/ibkr"))
  .dependsOn(commonUtil)
  .settings(
    name                                := "ibkr-tws",
    libraryDependencies ++= Libs.all,
    libraryDependencies += "com.odenzo" %% "ibkr-models" % "0.0.1-SNAPSHOT"
  )
