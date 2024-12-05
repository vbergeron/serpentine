import scala.scalanative.build.*

ThisBuild / scalaVersion := "3.5.2"

lazy val OpsMirror = "io.github.bishabosha" %% "ops-mirror" % "0.1.2"

lazy val serpentine = (project in file("modules/serpentine"))
  // .settings(libraryDependencies += OpsMirror)
  .enablePlugins(ScalaNativePlugin)

lazy val exemples = (project in file("modules/exemples"))
  .dependsOn(serpentine)
  .enablePlugins(ScalaNativePlugin)
  .settings(
    nativeConfig ~= {
      _.withBuildTarget(BuildTarget.libraryDynamic)
    }
  )

lazy val root = (project in file("."))
  .aggregate(serpentine, exemples)
