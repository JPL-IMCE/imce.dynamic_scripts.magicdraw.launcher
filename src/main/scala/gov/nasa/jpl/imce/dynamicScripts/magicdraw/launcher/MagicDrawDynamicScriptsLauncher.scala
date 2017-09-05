/*
 * Copyright 2017  California Institute of Technology ("Caltech").
 * U.S. Government sponsorship acknowledged.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * License Terms
 */

package gov.nasa.jpl.imce.dynamicScripts.magicdraw.launcher

import java.io.{BufferedInputStream, File, FileInputStream, FilenameFilter}
import java.lang.{IllegalArgumentException,Process,ProcessBuilder,System}
import java.nio.file.Path
import java.util.{Locale, Properties}

import scala.collection.immutable.{::,List,Nil,Seq}
import scala.util.{Failure, Success, Try}
import scala.util.control.Exception.nonFatalCatch
import scala.{Array, Boolean, Int, None, Option, Some, StringContext, Unit}
import scala.Predef.{augmentString,refArrayOps,String}

/**
  * Configuration:
  *
  * MagicDraw Installation:
  * - Either:
  *     Environment variable: MAGICDRAW_HOME=<dir>
  *     System property: -DMAGICDRAW_HOME=<dir>
  *   Check that the following folders exist in \$MAGICDRAW_HOME:
  *     bin/, configuration/, data/, lib/, lib/bundles/
  *   Check that bin/magicdraw.properties file exists and defines non-empty values for:
  *     JAVA_HOME, BOOT_CLASSPATH, CLASSPATH
  *
  * MagicDraw Config file:
  * - Either:
  *     System property: -Dmagicdraw.dynamicScripts.conf=<*.conf>
  *     \$MAGICDRAW_HOME/data/application.conf
  *
  * MagicDraw Logback config file:
  * - Either:
  *     System property: -Dmagicdraw.dynamicScripts.logback=<*.xml>
  *     \$MAGICDRAW_HOME/data/logback.xml
  *
  * MagicDraw dock name & icon (only for MacOSX):
  * - Either:
  *     System.property: -Dmagicdraw.dynamicScripts.name=<application name>
  *     "MagicDraw"
  * - \$MAGICDRAW_HOME/bin/md.icns
  *
  * MagicDraw memory allocation (-Xmx)
  * - Either:
  *     System property: -Dmagicdraw.dynamicScripts.Xmx=<value>
  *     4G
  *
  * MagicDraw stack allocation (-Xss)
  * - Either:
  *     System property: -Dmagicdraw.dynamicScripts.Xss=<value>
  *     16m
  *
  * @see https://docs.nomagic.com/display/MD185/Specifying+batch+mode+program+classpath+and+required+system+properties
  * @see https://docs.nomagic.com/display/MD185/Plugins+directories
  */
object MagicDrawDynamicScriptsLauncher {

  val osName: String = System.getProperty("os.name").toLowerCase(Locale.ENGLISH)
  val isWindows: Boolean = osName.contains("win")
  val isMacOSX: Boolean = osName.contains("mac")
  val isLinux: Boolean = osName.contains("linux")

  def getEnvOrProperty[V]
  (name: String,
   errorMessage: => String,
   valueConverter: (String, => String) => Try[V])
  : Try[V]
  = Option.apply(System.getProperty(name)).orElse(Option.apply(System.getenv(name))) match {
    case Some(v) =>
      valueConverter(v, errorMessage)
    case None =>
      Failure(new IllegalArgumentException(
        s"Run with either an environment variable $name=<value> or a system property -D$name=<name>"))
  }

  def convertClasspath(top: Path, cp: String): Try[Seq[String]]
  = cp
    .replaceAllLiterally("/", File.separator)
    .replaceAllLiterally("\\", File.separator)
    .replaceAllLiterally(":", File.pathSeparator)
    .replaceAllLiterally(";", File.pathSeparator)
    .split(File.pathSeparatorChar)
    .foldLeft[Try[Seq[String]]] {
    Success(Seq.empty[String])
  } {
    case (acc, path) =>
      for {
        paths <- acc
        path1 <- if (path.startsWith(File.separator))
          Success(path)
        else
          resolveFile("MD", top, path).map(_.toString)
      } yield paths :+ path1
  }

  def findSingleJar(dir: Path, prefix: String): Try[Path]
  = dir
    .toFile
    .list(new FilenameFilter() {
      override def accept(dir: File, name: String): Boolean
      = name.startsWith(prefix) && name.endsWith(".jar")
    })
    .to[List] match {
    case jar :: Nil =>
      val f = dir.resolve(jar).toFile
      if (f.exists() && f.canRead)
        Success(f.toPath)
      else
        Failure(new IllegalArgumentException(
          s"Failed to resolve jar=$jar as a file in $dir: $f"
        ))
    case Nil =>
      Failure(new IllegalArgumentException(
        s"No jars match '${prefix}*.jar' in $dir; there should be exactly 1"))
    case jars =>
      Failure(new IllegalArgumentException(
        jars.mkString(s"Found ${jars.size} jars matching '${prefix}*.jar' in $dir; there should be only 1!\n", "\n", "\n")))
  }

  def resolveFolder(prefix: String, home: Path, relPath: String): Try[Path]
  = {
    val dir = home.resolve(relPath).toFile
    if (dir.exists() && dir.isDirectory && dir.canRead && dir.canExecute)
      Success(dir.toPath)
    else
      Failure(new IllegalArgumentException(
        s"Failed to resolve $prefix's $relPath folder from $home as: $dir"
      ))
  }

  def resolveFile(prefix: String, home: Path, relPath: String): Try[Path]
  = resolveFile(prefix, home, relPath, home.resolve(relPath).toFile)

  def resolveFile(prefix: String, home: Path, relPath: String, f: File): Try[Path]
  = if (f.exists() && f.isFile && f.canRead)
    Success(f.toPath.toAbsolutePath)
  else
    Failure(new IllegalArgumentException(
      s"Failed to resolve $prefix's $relPath file from $home as: $f"
    ))

  def run(f: () => Try[Int]): Unit
  = f() match {
    case Success(status) =>
      System.exit(status)
    case Failure(t) =>
      System.err.println(t.getMessage)
      t.printStackTrace(System.err)
      System.exit(-1)
  }

  def main(args: Array[String]): Unit
  = run(Step1(args))

  def Step1(args: Array[String])(): Try[Int]
  = for {
    mdHome <-
    getEnvOrProperty(
      "MAGICDRAW_HOME",
      "Start either an environment variable MAGICDRAW_HOME=<dir> or a system property -DMAGICDRAW_HOME=<dir>",
      (s, errorMessage) => {
        val dir = new File(s)
        if (dir.exists() && dir.canRead)
          Success(dir.toPath.toAbsolutePath)
        else
          Failure(new IllegalArgumentException(errorMessage))
      })
    md <- MD.resolve(mdHome)
    dsHome <-
    Option.apply(System.getProperty("magicdraw.dynamicScripts.home")) match {
      case Some(h) =>
        val dir = new File(h)
        if (dir.isDirectory && dir.exists() && dir.canRead && dir.canExecute)
          Success(dir.toPath)
        else
          Failure(new IllegalArgumentException(
            s"Invalid IMCE magicdraw dynamic scripts tool directory: -Dmagicdraw.dynamicScripts.home=$h"
          ))
      case None =>
        Failure(new IllegalArgumentException(
          s"Start with -Dmagicdraw.dynamicScripts.home=<installation directory of the IMCE magicdraw dynamic scripts tool>"
        ))
    }
    ds <- DS.resolve(dsHome)
    result <- Step2(md,ds,args)
  } yield result

  case class MD
  (home: Path,
   bin: Path,
   data: Path,
   lib: Path,
   bundles: Path,
   configDir: Path,
   plugins: Path,
   props: Properties,
   mdClasspathArg: String,
   mdJavaHome: Path,
   mdJavaCmd: Path,
   mdBootClasspath: Seq[String],
   osgiLauncher: Path,
   osgiFramework: Path,
   osgiFragment: Path,
   mainClass: String)

  object MD {

    def resolve(mdHome: Path): Try[MD]
    = for {
      bin <- resolveFolder("MD", mdHome, "bin")
      data <- resolveFolder("MD", mdHome, "data")
      lib <- resolveFolder("MD", mdHome, "lib")
      bundles <- resolveFolder("MD", lib, "bundles")
      configDir <- resolveFolder("MD", mdHome, "configuration")
      plugins <- resolveFolder("MD", mdHome, "plugins")
      props <- resolveFile("MD", bin, "magicdraw.properties").flatMap { propFile =>
        nonFatalCatch[Try[Properties]]
          .withApply {
            (t: java.lang.Throwable) => Failure(t)
          }
          .apply {
            val mdProps = new Properties()
            val in = new BufferedInputStream(new FileInputStream(propFile.toFile))
            try {
              mdProps.load(in)
            } finally {
              in.close()
            }
            Success(mdProps)
          }
      }
      home = mdHome.toString
      leader = if (isWindows)
        home.replaceFirst("^", "/").replaceAll(" ", "%20").replaceAll("\\\\", "\\/")
      else
        home.replaceAll(" ", "%20")
      base = if (isWindows)
        home.replaceAll(":", "%3A").replaceAll(" ", "%20").replaceAll("\\/", "%2F").replaceAll("\\", "%5C")
      else
        home.replaceAll(" ", "%20")
      mdClasspathArg = s"-Dmd.class.path=file:$leader/bin/magicdraw.properties?base=$base#CLASSPATH"
      mdJavaHome <- {
        val javaHome = new File(props.getProperty("JAVA_HOME", ""))
        if (javaHome.exists && javaHome.canRead && javaHome.canExecute)
          Success(javaHome.toPath)
        else
          Failure(new IllegalArgumentException(
            s"The JAVA_HOME property in bin/magicdraw.properties, $javaHome, is invalid"))
      }
      mdJavaCmd <- {
        val javaBin = mdJavaHome.resolve("bin")
        if (isWindows) {
          val javaExe = javaBin.resolve("javaw.exe").toFile
          if (javaExe.exists && javaExe.canExecute)
            Success(javaExe.toPath)
          else
            Failure(new IllegalArgumentException(
              s"No $javaExe executable found"
            ))
        } else {
          val javaCmd = javaBin.resolve("java").toFile
          if (javaCmd.exists && javaCmd.canExecute)
            Success(javaCmd.toPath)
          else
            Failure(new IllegalArgumentException(
              s"No $javaCmd executable found"
            ))
        }
      }
      mdBootClasspath <-
      convertClasspath(mdHome, props.getProperty("BOOT_CLASSPATH", ""))

      osgiLauncher <- findSingleJar(lib, "com.nomagic.osgi.launcher-")
      osgiFramework <- findSingleJar(bundles, "org.eclipse.osgi_")
      osgiFragment <- findSingleJar(bundles, "com.nomagic.magicdraw.osgi.fragment_")
      mainClass <- Option.apply(props.getProperty("MAIN_CLASS")) match {
        case Some(cls) =>
          Success(cls)
        case None =>
          Failure(new IllegalArgumentException(
            s"No MAIN_CLASS property in bin/magicdraw.properties"
          ))
      }
    } yield MD(
      mdHome, bin, data, lib, bundles, configDir, plugins, props,
      mdClasspathArg, mdJavaHome, mdJavaCmd, mdBootClasspath,
      osgiLauncher, osgiFramework, osgiFragment,
      mainClass)

  }

  case class DS
  (home: Path,
   aspectjWeaver: Path,
   aspectjRuntime: Path,
   scalaRuntime: Path,
   enhancedAPI: Path,
   plugins: Path)

  object DS {

    def resolve(home: Path): Try[DS]
    = for {
      lib <- resolveFolder("DS", home, "lib")
      blib <- resolveFolder("DS", lib, "bootstrap")
      ajLib <- resolveFolder("DS", blib, "imce.third_party.aspectj")
      scLib <- resolveFolder("DS", blib, "imce.third_party.scala")
      iLib <- resolveFolder("DS", blib, "gov.nasa.jpl.imce")
      ajW <- findSingleJar(ajLib, "aspectjweaver-")
      ajR <- findSingleJar(ajLib, "aspectjrt-")
      scR <- findSingleJar(scLib, "scala-library-")
      ehAPI <- findSingleJar(iLib, "imce.magicdraw.library.enhanced_api_")
      plugins <- resolveFolder("DS", home, "plugins")
    } yield DS(home, ajW, ajR, scR, ehAPI, plugins)
  }

  def Step2(md: MD, ds: DS, args: Array[String]): Try[Int]
  = for {
    configFile <- resolveFile(
      "MD", md.data, "application.conf",
      new File(System.getProperty("magicdraw.dynamicScripts.conf", md.data.resolve("application.conf").toString)))
    logbackFile <- resolveFile(
      "MD", md.data, "logback.xml",
      new File(System.getProperty("magicdraw.dynamicScripts.logback", md.data.resolve("logback.xml").toString)))
    mxArg = System.getProperty("magicdraw.dynamicScripts.Xmx", "4G")
    ssArg = System.getProperty("magicdraw.dynamicScripts.Xss", "16m")
    dockName = if (isMacOSX)
      "-Xdock:name="+System.getProperty("magicdraw.dynamicScripts.name", "MagicDraw")
    else
      ""
    dockIcon <- if (isMacOSX) {
      val icon = md.bin.resolve("md.icns").toFile
      if (icon.exists && icon.canRead)
        Success(s"""-Xdock:icon=$icon""")
      else
        Failure(new IllegalArgumentException(
          s"No MD icon file found: $icon"
        ))
    } else
      Success("")
    proc <- Start(md, ds, configFile, logbackFile, mxArg, ssArg, dockName, dockIcon, args)
    status <- nonFatalCatch[Try[Int]]
      .withApply {
        (t: java.lang.Throwable) =>
          Failure(t)
      }
      .apply {
        Success(proc.waitFor())
      }
  } yield status

  def Start
  (md: MD,
   ds: DS,
   configFile: Path,
   logbackFile: Path,
   mxArg: String,
   ssArg: String,
   dockName: String,
   dockIcon: String,
   args: Array[String])
  : Try[Process]
  = for {
    j4 <- resolveFile("MD",md.lib, "md_api.jar")
    j5 <- resolveFile("MD",md.lib, "md_common_api.jar")
    j6 <- resolveFile("MD",md.lib, "md.jar")
    j7 <- resolveFile("MD",md.lib, "md_common.jar")
    j8 <- resolveFile("MD",md.lib, "jna.jar")
    cp = List(
      ds.aspectjWeaver.toString,
      ds.aspectjRuntime.toString,
      ds.scalaRuntime.toString,
      ds.enhancedAPI.toString,
      md.osgiLauncher.toString,
      md.osgiFramework.toString,
      md.osgiFragment.toString,
      j4,j5,j6,j7,j8).mkString(File.pathSeparator)
    cmd = List(
      md.mdJavaCmd.toString,
      s"-Xmx$mxArg",
      s"-Xss$ssArg",
      dockName, dockIcon,
      if (isMacOSX) "-Dapple.laf.useScreenMenuBar=true" else "",
      s"""-Dcom.nomagic.osgi.config.dir=${md.configDir.toString}""",
      s"""-Desi.system.config=${configFile.toString}""",
      s"""-Dlogback.configurationFile=${logbackFile.toString}""",
      s"""-Dmd.plugins.dir=${md.plugins}${File.pathSeparator}${ds.plugins}""",
      if (md.mdBootClasspath.nonEmpty)
        md.mdBootClasspath.mkString("-Xbootclasspath/p:",File.pathSeparator,"")
      else
        "",
      md.mdClasspathArg,
      "-cp",cp,
      md.mainClass
    ) ++ args
    _ = System.out.println(cmd.mkString("# Starting: \n#\t","\n#\t","\n"))
    pb = new ProcessBuilder(cmd:_ *).inheritIO()
  } yield pb.start()

}
