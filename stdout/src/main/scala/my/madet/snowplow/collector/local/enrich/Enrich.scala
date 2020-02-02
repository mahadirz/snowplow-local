/*
 * Copyright (c) 2013-2019 Snowplow Analytics Ltd.
 * All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache
 * License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 *
 * See the Apache License Version 2.0 for the specific language
 * governing permissions and limitations there under.
 */

package my.madet.snowplow.collector.local.enrich

import java.io.{File, PrintWriter}
import java.net.URI
import java.nio.file.{Files, Paths}
import java.util.UUID.randomUUID

import _root_.io.circe.Json
import cats.Id
import cats.data.Validated
import com.snowplowanalytics.iglu.client.Client
import com.snowplowanalytics.snowplow.analytics.scalasdk.Event
import com.snowplowanalytics.snowplow.badrows.Processor
import my.madet.snowplow.collector.local.enrich.config._
import my.madet.snowplow.collector.local.enrich.singleton._
import my.madet.snowplow.collector.local.enrich.utils._
import com.snowplowanalytics.snowplow.collectors.scalastream.generated
import com.snowplowanalytics.snowplow.collectors.scalastream.generated.BuildInfo
import com.snowplowanalytics.snowplow.enrich.common.EtlPipeline
import com.snowplowanalytics.snowplow.enrich.common.adapters.AdapterRegistry
import com.snowplowanalytics.snowplow.enrich.common.enrichments.EnrichmentRegistry
import com.snowplowanalytics.snowplow.enrich.common.enrichments.registry.EnrichmentConf
import com.snowplowanalytics.snowplow.enrich.common.loaders.ThriftLoader
import com.snowplowanalytics.snowplow.enrich.common.outputs.EnrichedEvent
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import io.circe.parser._

class Enrich(config: ParsedEnrichConfig) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  // cache necessary files from the enrichments
  cacheEnrich(config.enrichmentConfs)

  def run(data: Array[Byte]): Unit = {
    //println((data.map(_.toChar)).mkString)
    val enriched = enrich(
      data,
      EnrichmentRegistrySingleton.get(config.enrichmentConfs),
      ClientSingleton.get(config.resolver)
    )

    val enrichedPartitioned = enriched.partition(_.isValid)
    enrichedPartitioned._1
      .collect {
        case Validated.Valid(enriched) =>
          val tsv = tabSeparatedEnrichedEvent(enriched)
          // lossy true will flatten unstruct name
          Event
            .parse(tsv)
            .map(event => event.toJson(true))
            .fold(
              e => {
                logger.error("Parsing enriched failed!: " + e)
              },
              json => {
                save(json, config.enriched)
              }
            )
      }

    val failures = enrichedPartitioned._2.collect {
      case Validated.Invalid(badRows) => badRows.toList
    }.flatten

    failures.foreach(x => {
      logger.error("Error in enrichment step!! You added something to tracker?")
      parse(x).fold(e => {
        logger.error("Failed to parse json: " + e)
      }, json => {
        save(json, config.bad)
      })

    })
  }

  private def save(json: Json, path: String): Unit = {
    //@todo partition based on event name and datetime
    val dir = Paths.get(path)
    if (!Files.exists(dir)) {
      // this repeated operation make sense once partition implemented
      // but caching probably helps
      // also convenience to delete entire enrich directory in runtime
      Files.createDirectories(dir)
    }
    //save to file using random name
    val dest = Paths.get(path, randomUUID.toString + ".json")
    val pw = new PrintWriter(dest.toFile)
    pw.write(json.toString())
    pw.close()
  }

  /**
   * Enrich a collector payload into a list of [[EnrichedEvent]].
   *
   * @param data serialized collector payload
   * @return a list of either [[EnrichedEvent]] or [[BadRow]]
   */
  private def enrich(
    data: Array[Byte],
    enrichmentRegistry: EnrichmentRegistry[Id],
    client: Client[Id, Json]
  ): List[Validated[List[String], EnrichedEvent]] = {

    val processor = Processor(BuildInfo.name, generated.BuildInfo.version)
    val collectorPayload = ThriftLoader.toCollectorPayload(data, processor)
    val enriched = EtlPipeline.processEvents(
      new AdapterRegistry,
      enrichmentRegistry,
      client,
      processor,
      new DateTime(System.currentTimeMillis),
      collectorPayload
    )
    enriched.map {
      _.leftMap(_.map(br => br.compact).toList)
    }
  }

  /**
   * Handle the needed files and create the necessary
   * symlinks.
   *
   * @param enrichmentConfs list of enrichment configurations
   */
  private def cacheEnrich(enrichmentConfs: List[EnrichmentConf]): Unit = {
    val filesToCache: List[(URI, String)] = enrichmentConfs
      .map(_.filesToCache)
      .flatten
      .map { case (uri, sl) => (uri, sl) }
    for (x <- filesToCache) {
      //println(x)
      //println(x._1.getClass)
      val file = x._1.getPath
      val symfile = x._2
      createSymLink(new File(file), symfile).fold(e => {
        logger.warn(s"File $file could not be cached: $e")
      }, p => {
        logger.info(s"File $file cached at $p")
      })
    }

  }

}
