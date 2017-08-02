package amf.builder

import amf.domain._
import amf.metadata.domain.RequestModel._

/**
  * Request domain element builder.
  */
class RequestBuilder extends Builder {
  override type T = Request

  def withQueryParameters(queryParameters: Seq[Parameter]): RequestBuilder = set(QueryParameters, queryParameters)

  def withHeaders(headers: Seq[Parameter]): RequestBuilder = set(Headers, headers)

  def withPayloads(payloads: Seq[Payload]): RequestBuilder = set(Payloads, payloads)

  override def build: Request = Request(fields, annotations)
}

object RequestBuilder {
  def apply(): RequestBuilder = apply(Nil)

  def apply(fields: Fields, annotations: List[Annotation] = Nil): RequestBuilder = apply(annotations).copy(fields)

  def apply(annotations: List[Annotation]): RequestBuilder = new RequestBuilder().withAnnotations(annotations)
}
