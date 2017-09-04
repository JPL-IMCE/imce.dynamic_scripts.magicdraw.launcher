
import sbt.Keys._
import sbt._
import com.typesafe.sbt.packager.SettingsHelper
import gov.nasa.jpl.imce.sbt._

lazy val thisVersion = SettingKey[String]("this-version", "This Module's version")

lazy val launcher =
  Project("imce-dynamic_scripts-magicdraw-launcher", file("."))
    .enablePlugins(IMCEGitPlugin)
    .enablePlugins(JavaAppPackaging)
    .enablePlugins(UniversalPlugin)
    .settings(IMCEPlugin.strictScalacFatalWarningsSettings)

    .settings(
      IMCEKeys.licenseYearOrRange := "2014-2017",
      IMCEKeys.organizationInfo := IMCEPlugin.Organizations.cae,
      IMCEKeys.targetJDK := IMCEKeys.jdk18.value,
      git.baseVersion := Versions.version,

      thisVersion := version.value,

      buildInfoPackage := "gov.nasa.jpl.imce.dynamic_scripts.launcher",
      buildInfoKeys ++= Seq[BuildInfoKey](BuildInfoKey.action("buildDateUTC") { buildUTCDate.value }),

      projectID := {
        val previous = projectID.value
        previous.extra(
          "build.date.utc" -> buildUTCDate.value,
          "artifact.kind" -> "magicdraw.resource.plugin")
      },
      resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases",
      scalacOptions in (Compile, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg",
      scalacOptions in (Test, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg",
      scalacOptions in (Compile, doc) += "-Xplugin-disable:artima-supersafe",
      scalacOptions in (Test, doc) += "-Xplugin-disable:artima-supersafe",

      mainClass in Compile := Some("gov.nasa.jpl.imce.dynamicScripts.magicdraw.launcher.MagicDrawDynamicScriptsLauncher"),

      executableScriptName := "imceMDLauncher",

      SettingsHelper.makeDeploymentSettings(Universal, packageZipTarball in Universal, "tgz"),

      packagedArtifacts in publish += {
        val p = (packageZipTarball in Universal).value
        val n = (name in Universal).value
        Artifact(n, "tgz", "tgz", Some("resource"), Seq(), None, Map()) -> p
      },
      packagedArtifacts in publishLocal += {
        val p = (packageZipTarball in Universal).value
        val n = (name in Universal).value
        Artifact(n, "tgz", "tgz", Some("resource"), Seq(), None, Map()) -> p
      },
      packagedArtifacts in publishM2 += {
        val p = (packageZipTarball in Universal).value
        val n = (name in Universal).value
        Artifact(n, "tgz", "tgz", Some("resource"), Seq(), None, Map()) -> p
      }

    )
