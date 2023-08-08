package org.zalando.kanadi.models

import org.apache.pekko.http.scaladsl.model.headers.RawHeader

final case class CustomHeaders(headers: Map[String, String]) extends AnyVal {
  def toRawHeaders: List[RawHeader] =
    headers.toList.map { case (k, v) => RawHeader(k, v) }
}
