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
package my.madet.snowplow.collector.local

import cats.Id
import com.snowplowanalytics.iglu.client.Client
import com.snowplowanalytics.snowplow.collectors.scalastream.Collector
import my.madet.snowplow.collector.local.enrich.config._
import my.madet.snowplow.collector.local.enrich.utils._
import com.snowplowanalytics.snowplow.collectors.scalastream.model._
import com.snowplowanalytics.snowplow.enrich.common.enrichments.EnrichmentRegistry
import my.madet.snowplow.collector.local.enrich.Enrich
import my.madet.snowplow.collector.local.sinks.StdoutSink

object StdoutCollector extends Collector {
  def main(args: Array[String]): Unit = {
    val (collectorConf, akkaConf) = parseConfig(args)

    val resolverJson = parseResolver(collectorConf.enrich.resolver)
    val client = Client.parseDefault[Id](resolverJson.right.get).leftMap(_.toString).value
    val registryJson = parseEnrichmentRegistry(collectorConf.enrich.enrichments, client.right.get)
    val confs = EnrichmentRegistry
      .parse(registryJson.right.get, client.right.get, false)
      .leftMap(_.toString)
      .toEither

    val parsedConfig = ParsedEnrichConfig(
      collectorConf.enrich.enriched,
      collectorConf.enrich.bad,
      collectorConf.enrich.pii,
      resolverJson.right.get,
      confs.right.get
    )

    val enrich = new Enrich(parsedConfig)

    val sinks = {
      val (good, bad) = collectorConf.streams.sink match {
        case Stdout => (new StdoutSink("out", enrich), new StdoutSink("err", enrich))
        case _ => throw new IllegalArgumentException("Configured sink is not stdout")
      }
      CollectorSinks(good, bad)
    }
    run(collectorConf, akkaConf, sinks)
  }
}
