/*
 * Copyright (c) 2012-2020 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and
 * limitations there under.
 */
package my.madet.snowplow.collector.local.enrich

import java.io.File

import cats.Id
import cats.implicits._
import com.snowplowanalytics.iglu.client.Client
import com.snowplowanalytics.iglu.core._
import com.snowplowanalytics.iglu.core.circe.CirceIgluCodecs._
import com.snowplowanalytics.snowplow.enrich.common.enrichments.EnrichmentRegistry
import com.snowplowanalytics.snowplow.enrich.common.enrichments.registry.EnrichmentConf
import com.snowplowanalytics.snowplow.enrich.common.utils.JsonUtils
import io.circe.Json
import io.circe.syntax._
//import utils._
import utils.idClock

import scala.io.Source

object config {
  private type EitherS[A] = Either[String, A]

  /** Case class holding the raw job configuration */
  final case class EnrichConfig(
    enriched: String,
    bad: String,
    pii: Option[String],
    resolver: String,
    enrichments: Option[String]
  )

  /** ADT for configuration parameters */
  sealed trait Configuration {
    def key: String
    def desc: String
  }
  final case class OptionalConfiguration(key: String, desc: String) extends Configuration
  final case class RequiredConfiguration(key: String, desc: String) extends Configuration

  /** Case class holding the parsed job configuration */
  final case class ParsedEnrichConfig(
    enriched: String,
    bad: String,
    pii: Option[String],
    resolver: Json,
    enrichmentConfs: List[EnrichmentConf]
  )

  /**
   * Parses a resolver at the specified path.
   * @param resolverPath path where the resolver is located
   * @return the parsed JValue if the parsing was successful
   */
  def parseResolver(resolverPath: String): Either[String, Json] =
    for {
      fileContent <- readResolverFile(resolverPath)
      json <- JsonUtils.extractJson(fileContent)
      _ <- Client.parseDefault[Id](json).leftMap(_.message).value
    } yield json

  /** Reads a resolver file at the specfied path. */
  private def readResolverFile(path: String): Either[String, String] = {
    val file = new File(path)
    if (file.exists) Source.fromFile(file).mkString.asRight
    else s"Iglu resolver configuration file `$path` does not exist".asLeft
  }

  /**
   * Parses an enrichment registry at the specified path.
   * @param enrichmentsPath path where the enrichment directory is located
   * @return the enrichment registry built from the enrichments found
   */
  def parseEnrichmentRegistry(
    enrichmentsPath: Option[String],
    client: Client[Id, Json]
  ): Either[String, Json] =
    for {
      fileContents <- readEnrichmentFiles(enrichmentsPath)
      jsons <- fileContents.map(JsonUtils.extractJson(_)).sequence[EitherS, Json]
      schemaKey = SchemaKey(
        "com.snowplowanalytics.snowplow",
        "enrichments",
        "jsonschema",
        SchemaVer.Full(1, 0, 0)
      )
      enrichmentsJson = SelfDescribingData[Json](schemaKey, Json.fromValues(jsons)).asJson
      _ <- EnrichmentRegistry
        .parse(enrichmentsJson, client, false)
        .leftMap(_.toList.mkString("\n"))
        .toEither
    } yield enrichmentsJson

  /** Reads all the enrichment files contained in a directory at the specified path. */
  private def readEnrichmentFiles(path: Option[String]): Either[String, List[String]] =
    path
      .map { p =>
        for {
          files <- Option(new File(p).listFiles)
            .toRight(s"Enrichment directory `$p` does not exist")
          read = files
            .filter(_.getName.endsWith(".json"))
            .map(Source.fromFile(_).mkString)
            .toList
        } yield read
      }
      .getOrElse(Nil.asRight)
}
