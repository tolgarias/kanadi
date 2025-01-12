package org.zalando.kanadi
package api

import java.util.UUID
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.zalando.kanadi.models.{EventId, EventTypeName, PublishedBy, SpanCtx}

import java.time.OffsetDateTime

class JsonSpec extends Specification {
  override def is: SpecStructure = s2"""
    Parse business events         $businessEvent
    Parse data events             $dataEvent
    Parse undefined events        $undefinedEvent
    SpanCtx decoding example      $decodeSpnCtx
    SpanCtx encoding example      $encodeSpnCtx
    SpanCtx fail decoding example $badDecodeSpnCtx
    Decoding EventType example    $decodeEventTypesAnnotationsCtx
    Encoding EventType example    $encodeEventTypesAnnotationsCtx
    """

  val uuid      = UUID.randomUUID()
  val testEvent = SomeEvent("Bart", "Simpson", uuid)
  val now       = OffsetDateTime.now()
  val md = Metadata(eid = EventId(UUID.fromString("4ae5011e-eb01-11e5-8b4a-1c6f65464fc6")),
                    occurredAt = now,
                    publishedBy = Some(PublishedBy("bart_simpson")))

  val coreEventJson = s"""
    "first_name": "Bart",
    "last_name": "Simpson",
    "uuid": "${uuid.toString}"
  """

  val metadata =
    s""""eid": "4ae5011e-eb01-11e5-8b4a-1c6f65464fc6", "occurred_at": ${now.asJson}, "published_by": "bart_simpson""""

  val businessEventJson = s"""{
    "metadata": {$metadata},
    $coreEventJson
  }"""

  val dataEventJson = s"""{
    "metadata": {$metadata},
    "data_op": "C",
    "data": {$coreEventJson},
    "data_type": "blah"
  }"""

  val undefinedEventJson = s"{$coreEventJson}"

  val eventTypeWithAnnotationsJson =
    """{
      |  "name" : "order.order_cancelled",
      |  "owning_application" : "price-service",
      |  "category" : "undefined",
      |  "enrichment_strategies" : [
      |    "metadata_enrichment"
      |  ],
      |  "schema" : {
      |    "type" : "json_schema",
      |    "schema" : "{\"type\":\"object\"}"
      |  },
      |  "annotations" : {
      |    "nakadi.io/internal-event-type" : "true",
      |    "criticality" : "low"
      |  }
      |}""".stripMargin

  def businessEvent =
    decode[Event[SomeEvent]](businessEventJson) must beRight(Event.Business(testEvent, md))

  def dataEvent =
    decode[Event[SomeEvent]](dataEventJson) must beRight(Event.DataChange(testEvent, "blah", DataOperation.Create, md))

  def undefinedEvent =
    decode[Event[SomeEvent]](undefinedEventJson) must beRight(Event.Undefined(testEvent))

  // Sample data is taken from official Nakadi source at https://github.com/zalando/nakadi/blob/effb2ed7e95bd329ab73ce06b2857aa57510e539/src/test/java/org/zalando/nakadi/validation/JSONSchemaValidationTest.java

  val spanCtxJson =
    """{"eid":"04ba01db-9990-44bd-b733-be69008c5da3","occurred_at":"1992-08-03T10:00:00Z","span_ctx":{"ot-tracer-spanid":"b268f901d5f2b865","ot-tracer-traceid":"e9435c17dabe8238","ot-baggage-foo":"bar"}}"""

  val spanCtxBadJson =
    """{"eid":"04ba01db-9990-44bd-b733-be69008c5da3","occurred_at":"1992-08-03T10:00:00Z","span_ctx":{"ot-tracer-spanid":"b268f901d5f2b865","ot-tracer-traceid":42,"ot-baggage-foo":"bar"}}"""

  val spanCtxEventMetadata = Metadata(
    eid = EventId(UUID.fromString("04ba01db-9990-44bd-b733-be69008c5da3")),
    occurredAt = OffsetDateTime.parse("1992-08-03T10:00:00Z"),
    spanCtx = Some(
      SpanCtx(
        Map(
          "ot-tracer-spanid"  -> "b268f901d5f2b865",
          "ot-tracer-traceid" -> "e9435c17dabe8238",
          "ot-baggage-foo"    -> "bar"
        )))
  )

  val eventTypeWithAnnotationsData = EventType(
    name = EventTypeName("order.order_cancelled"),
    owningApplication = "price-service",
    category = Category.Undefined,
    enrichmentStrategies = List(EnrichmentStrategy.MetadataEnrichment),
    schema = EventTypeSchema.anyJsonObject,
    annotations = Some(Map("nakadi.io/internal-event-type" -> "true", "criticality" -> "low"))
  )

  def decodeSpnCtx =
    decode[Metadata](spanCtxJson) must beRight(spanCtxEventMetadata)

  def encodeSpnCtx =
    spanCtxEventMetadata.asJson.printWith(Printer.noSpaces.copy(dropNullValues = true)) mustEqual spanCtxJson

  def badDecodeSpnCtx =
    decode[Metadata](spanCtxBadJson) must beLeft

  def decodeEventTypesAnnotationsCtx =
    decode[EventType](eventTypeWithAnnotationsJson) must beRight(eventTypeWithAnnotationsData)

  def encodeEventTypesAnnotationsCtx =
    eventTypeWithAnnotationsData.asJson.printWith(
      Printer.spaces2.copy(dropNullValues = true)) mustEqual eventTypeWithAnnotationsJson
}
