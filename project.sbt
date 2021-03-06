
sbtPlugin := false

name := "imce.dynamic_scripts.magicdraw.launcher"

description := "Launcher for IMCE's MagicDraw DynamicScripts plugin"

moduleName := name.value

organization := "gov.nasa.jpl.imce"

homepage := Some(url(s"https://jpl-imce.github.io/${moduleName.value}"))

organizationName := "JPL-IMCE"

organizationHomepage := Some(url(s"https://github.com/${organizationName.value}"))

git.remoteRepo := s"git@github.com:${organizationName.value}/${moduleName.value}"

scmInfo := Some(ScmInfo(
  browseUrl = url(s"https://github.com/${organizationName.value}/${moduleName.value}"),
  connection = "scm:"+git.remoteRepo.value))

developers := List(
  Developer(
    id="NicolasRouquette",
    name="Nicolas F. Rouquette",
    email="nicolas.f.rouquette@jpl.nasa.gov",
    url=url("https://github.com/NicolasRouquette")))

