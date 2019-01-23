package amf.plugins.document.webapi.parser.spec.domain

import amf.core.model.domain.{DomainElement, Linkable, NamedDomainElement}
import amf.plugins.document.webapi.annotations.FormBodyParameter
import amf.plugins.domain.webapi.models.{Parameter, Payload}
import org.yaml.model.YPart

case class Parameters(query: Seq[Parameter] = Nil,
                      path: Seq[Parameter] = Nil,
                      header: Seq[Parameter] = Nil,
                      baseUri08: Seq[Parameter] = Nil,
                      body: Seq[Payload] = Nil) {
  def merge(inner: Parameters): Parameters = {
    Parameters(
      mergeParams(query, inner.query),
      mergeParams(path, inner.path),
      mergeParams(header, inner.header),
      mergeParams(baseUri08, inner.baseUri08),
      mergePayloads(body, inner.body)
    )
  }

  def add(inner: Parameters): Parameters = {
    Parameters(
      addParams(query, inner.query),
      addParams(path, inner.path),
      addParams(header, inner.header),
      addParams(baseUri08, inner.baseUri08),
      addPayloads(body, inner.body)
    )
  }

  private def mergeParams(global: Seq[Parameter], inner: Seq[Parameter]): Seq[Parameter] = {
    val globalMap = global.map(p => p.name.value() -> p).toMap
    val innerMap  = inner.map(p => p.name.value()  -> p).toMap

    (globalMap ++ innerMap).values.toSeq
  }

  private def mergePayloads(global: Seq[Payload], inner: Seq[Payload]): Seq[Payload] = inner

  private def addParams(global: Seq[Parameter], inner: Seq[Parameter]): Seq[Parameter] = {
    val globalMap = global.map(p => p.name.value() -> p).toMap
    val innerMap  = inner.map(p => p.name.value()  -> p).toMap

    (globalMap ++ innerMap).values.toSeq
  }

  private def addPayloads(global: Seq[Payload], inner: Seq[Payload]): Seq[Payload] = global ++ inner

  def nonEmpty: Boolean = query.nonEmpty || path.nonEmpty || header.nonEmpty || body.nonEmpty
}

object Parameters {
  def classified(path: String, params: Seq[Parameter], payloads: Seq[Payload] = Nil): Parameters = {
    var uriParams: Seq[Parameter]  = Nil
    var pathParams: Seq[Parameter] = Nil
    params.filter(_.isPath).foreach { param =>
      if (path.contains(s"{${param.name.value()}}"))
        pathParams ++= Seq(param)
      else
        uriParams ++= Seq(param)
    }
    Parameters(params.filter(_.isQuery), pathParams, params.filter(_.isHeader), uriParams, payloads)
  }
}

/**
  * I need to be sure that always i will have either a param or a payload.
  */
class OasParameter(element: Either[Parameter, Payload], val ast: Option[YPart] = None) {

  val isFormData: Boolean = element.right.toOption.exists(
    p =>
      (p.isLink && p.effectiveLinkTarget().annotations.contains(classOf[FormBodyParameter])) || p.annotations.contains(
        classOf[FormBodyParameter]))
  val isBody: Boolean             = element.isRight && !isFormData
  private val paramOption         = element.left.toOption
  def query: Option[Parameter]    = paramOption.filter(_.isQuery)
  def path: Option[Parameter]     = paramOption.filter(_.isPath)
  def header: Option[Parameter]   = paramOption.filter(_.isHeader)
  def invalids: Option[Parameter] = paramOption.filter(p => !p.isQuery && !p.isHeader && !p.isPath)

  def formData: Option[Payload] = if (isFormData) element.right.toOption else None
  def body: Option[Payload]     = if (!isFormData) element.right.toOption else None

  val domainElement: DomainElement with Linkable with NamedDomainElement = element match {
    case Left(p)  => p
    case Right(p) => p
  }
}

object OasParameter {
  def apply(parameter: Parameter, ast: Option[YPart]): OasParameter = new OasParameter(Left(parameter), ast)
  def apply(parameter: Parameter): OasParameter                     = OasParameter(parameter, None)
  def apply(payload: Payload, ast: Option[YPart]): OasParameter     = new OasParameter(Right(payload), ast)
  def apply(payload: Payload): OasParameter                         = OasParameter(payload, None)
}
