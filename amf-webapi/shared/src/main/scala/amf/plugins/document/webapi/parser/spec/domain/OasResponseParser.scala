package amf.plugins.document.webapi.parser.spec.domain

import amf.core.annotations.{AutoGeneratedName, DeclaredElement, TrackedElement}
import amf.core.model.domain.AmfArray
import amf.core.parser.{Annotations, ScalarNode, SearchScope, _}
import amf.plugins.document.webapi.annotations.DefaultPayload
import amf.plugins.document.webapi.contexts.OasWebApiContext
import amf.plugins.document.webapi.parser.spec.OasDefinitions
import amf.plugins.document.webapi.parser.spec.common.{AnnotationParser, SpecParserOps}
import amf.plugins.document.webapi.parser.spec.declaration.OasTypeParser
import amf.plugins.document.webapi.parser.spec.oas.{Oas2Syntax, Oas3Syntax}
import amf.plugins.domain.shapes.models.AnyShape
import amf.plugins.domain.shapes.models.ExampleTracking.tracking
import amf.plugins.domain.webapi.metamodel.{PayloadModel, RequestModel, ResponseModel}
import amf.plugins.domain.webapi.models.{Parameter, Payload, Response}
import org.yaml.model.YMap
import amf.core.utils.AmfStrings
import amf.plugins.document.webapi.parser.spec.WebApiDeclarations.ErrorResponse
import amf.plugins.domain.webapi.metamodel.ResponseModel.Headers
import amf.plugins.features.validation.CoreValidations

import scala.collection.mutable

case class OasResponseParser(map: YMap, adopted: Response => Unit)(implicit ctx: OasWebApiContext)
    extends SpecParserOps {
  def parse(): Response = {

    ctx.link(map) match {
      case Left(url) =>
        val name = OasDefinitions.stripResponsesDefinitionsPrefix(url)
        ctx.declarations
          .findResponse(name, SearchScope.Named)
          .map { res =>
            val resLink: Response = res.link(name)
            adopted(resLink)
            resLink
          }
          .getOrElse {
            ctx.obtainRemoteYNode(url) match {
              case Some(respNode) =>
                OasResponseParser(respNode.as[YMap], adopted).parse()
              case None =>
                ctx.violation(CoreValidations.UnresolvedReference, "", s"Cannot find response reference $url", map)
                val errorRes: Response = ErrorResponse(url, map).link(name)
                adopted(errorRes)
                errorRes
            }
          }
      case Right(_) =>
        val res = Response()
        adopted(res)

        map.key("description", ResponseModel.Description in res)

        map.key(
          "headers",
          entry => {
            val parameters: Seq[Parameter] =
              OasHeaderParametersParser(entry.value.as[YMap], { header =>
                header.adopted(res.id)
                res.add(Headers, header)
              }).parse()
            res.set(RequestModel.Headers, AmfArray(parameters, Annotations(entry.value)), Annotations(entry))
          }
        )

        val payloads = mutable.ListBuffer[Payload]()

        // OAS 2.0
        if (ctx.syntax == Oas2Syntax) {

          defaultPayload(map, res.id).foreach(payloads += _)

          map.key(
            "examples",
            entry => {
              val examples = OasResponseExamplesParser(entry).parse()
              if (examples.nonEmpty) {
                examples.foreach(_.annotations += TrackedElement(res.id))
              }
              res.set(ResponseModel.Examples, AmfArray(examples, Annotations(entry.value)), Annotations(entry))
            }
          )

          ctx.closedShape(res.id, map, "response")
        }

        // RAML 1.0 extensions
        map.key(
          "responsePayloads".asOasExtension,
          entry =>
            entry.value
              .as[Seq[YMap]]
              .map(value => payloads += OasPayloadParser(value, res.withPayload).parse())
        )

        // OAS 3.0.0
        if (ctx.syntax == Oas3Syntax) {
          map.key(
            "content",
            entry => payloads ++= OasContentsParser(entry, res.withPayload).parse()
          )

          map.key(
            "links",
            entry =>
              entry.value
                .as[YMap]
                .entries
                .foreach { entry =>
                  val linkName = ScalarNode(entry.key).text().value.toString
                  OasLinkParser(entry.value, linkName, link => res.add(ResponseModel.Links, link).adopted(res.id))
                    .parse()
              }
          )
        }

        if (payloads.nonEmpty)
          res.set(ResponseModel.Payloads, AmfArray(payloads))

        AnnotationParser(res, map).parse()

        res
    }
//    if (!response.annotations.contains(classOf[DeclaredElement])) {
//      if (response.name.is("default")) {
//        response.set(ResponseModel.StatusCode, "200")
//      } else {
//        response.set(ResponseModel.StatusCode, node)
//      }
//    }
  }

  private def defaultPayload(entries: YMap, parentId: String): Option[Payload] = {
    val payload = Payload().add(DefaultPayload())

    entries.key("mediaType".asOasExtension,
                entry => payload.set(PayloadModel.MediaType, ScalarNode(entry.value).string(), Annotations(entry)))
    // TODO add parent id to payload?
    payload.adopted(parentId)

    entries.key(
      "schema",
      entry => {
        val shape = OasTypeParser(entry, shape => shape.withName("default").adopted(payload.id))
          .parse()
          .map { s =>
            if (!s.isLink || (s.isLink && !s.linkTarget.exists(_.annotations.contains(classOf[DeclaredElement]))))
              s.add(AutoGeneratedName()) //if schema is defined inline or links to a external file it will have the autogenerated default name
            tracking(s, payload.id)
          }
        payload.set(PayloadModel.Schema, shape.getOrElse(AnyShape(entry.value)), Annotations(entry))
        payload.annotations ++= Annotations(entry)
      }
    )

    if (payload.fields.nonEmpty) Some(payload) else None
  }
}
