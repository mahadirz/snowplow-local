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
package my.madet.snowplow.collector.local.sinks

import com.snowplowanalytics.snowplow.collectors.scalastream.sinks.Sink
import my.madet.snowplow.collector.local.enrich.Enrich

class StdoutSink(streamName: String, enrich: Enrich) extends Sink {
  override val MaxBytes = Int.MaxValue
  def storeRawEvents(events: List[Array[Byte]], key: String) = {
    if (events.nonEmpty)
      log.debug(s"Receiving ${events.size} Thrift records")
    streamName match {
      case "out" =>
        events foreach { e =>
          //println(Base64.encodeBase64String(e))
          //println((e.map(_.toChar)).mkString)
          enrich.run(e)
        }
      case "err" =>
        events foreach { e =>
          Console.err.println("Error")
          Console.err.println(e)
        }
    }
    Nil
  }
}
