/*
 * Copyright (c) 2013-2020 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0, and
 * you may not use this file except in compliance with the Apache License
 * Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Apache License Version 2.0 is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the Apache License Version 2.0 for the specific language
 * governing permissions and limitations there under.
 */

lazy val commonDependencies = Seq(
  // Java
  Dependencies.Libraries.jodaTime,
  Dependencies.Libraries.slf4j,
  Dependencies.Libraries.log4jOverSlf4j,
  Dependencies.Libraries.config,
  Dependencies.Libraries.prometheus,
  Dependencies.Libraries.prometheusCommon,
  // Scala
  Dependencies.Libraries.scopt,
  Dependencies.Libraries.scalaz7,
  Dependencies.Libraries.akkaStream,
  Dependencies.Libraries.akkaHttp,
  Dependencies.Libraries.akkaStream,
  Dependencies.Libraries.akkaSlf4j,
  Dependencies.Libraries.badRows,
  Dependencies.Libraries.collectorPayload,
  Dependencies.Libraries.pureconfig,
  // temporarily exclude joda money, resolving dedup
  (Dependencies.Libraries.snowplowEnrich).exclude("org.joda","joda-money"),
  Dependencies.Libraries.snowplowSDK,
  // Scala (test)
  Dependencies.Libraries.akkaTestkit,
  Dependencies.Libraries.akkaHttpTestkit,
  Dependencies.Libraries.akkaStreamTestkit,
  Dependencies.Libraries.specs2
)

lazy val buildSettings = Seq(
  organization := "my.madet",
  name := "snowplow-local-collector",
  version := "0.1",
  description := "Scala Stream Collector for Snowplow raw events and sink to local",
  scalaVersion := "2.12.10",
  resolvers ++= Dependencies.resolutionRepos
)

lazy val allSettings = buildSettings ++
  BuildSettings.sbtAssemblySettings ++
  BuildSettings.formatting ++
  Seq(libraryDependencies ++= commonDependencies)

lazy val root = project.in(file("."))
  .settings(buildSettings)
  .aggregate(core, stdout)

lazy val sceVersion = "1.0.0"

lazy val core = project
  .settings(moduleName := "snowplow-stream-collector-core")
  .settings(buildSettings)
  .settings(libraryDependencies ++= commonDependencies)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](organization, name, version, "shortName" -> "ssc", scalaVersion, "sceVersion" -> sceVersion),
    buildInfoPackage := "com.snowplowanalytics.snowplow.collectors.scalastream.generated"
  )

lazy val stdout = project
  .settings(moduleName := "snowplow-local")
  .settings(allSettings)
  .dependsOn(core)