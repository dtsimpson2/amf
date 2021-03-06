package amf.plugins.document.webapi.parser.spec.common

import amf.core.AMFSerializer
import amf.core.emitter.BaseEmitters._
import amf.core.emitter.{EntryEmitter, ShapeRenderOptions, SpecOrdering}
import amf.core.model.document.Document
import amf.core.model.domain.DomainElement
import amf.core.parser.Position
import amf.core.remote.JsonSchema
import amf.core.services.RuntimeSerializer
import amf.plugins.document.webapi.annotations.{GeneratedJSONSchema, JSONSchemaRoot, ParsedJSONSchema}
import amf.plugins.document.webapi.contexts.JsonSchemaEmitterContext
import amf.plugins.document.webapi.parser.spec.OasDefinitions
import amf.plugins.document.webapi.parser.spec.oas.OasDeclarationsEmitter
import amf.plugins.domain.shapes.models.AnyShape
import org.yaml.model.YDocument
import org.yaml.model.YDocument.EntryBuilder

trait JsonSchemaSerializer {
  // todo, check if its resolved?
  // todo lexical ordering?

  protected def toJsonSchema(element: AnyShape): String = {
    element.annotations.find(classOf[ParsedJSONSchema]) match {
      case Some(a) => a.rawText
      case _ =>
        element.annotations.find(classOf[GeneratedJSONSchema]) match {
          case Some(g) => g.rawText
          case _       => generateJsonSchema(element)
        }
    }
  }

  protected def generateJsonSchema(element: AnyShape, options: ShapeRenderOptions = ShapeRenderOptions()): String = {
    AMFSerializer.init()
    val originalId = element.id
    val document   = Document().withDeclares(Seq(fixNameIfNeeded(element)) ++ element.closureShapes)
    val jsonSchema = RuntimeSerializer(document, "application/schema+json", JsonSchema.name, shapeOptions = options)
    element.withId(originalId)
    element.annotations.reject(a =>
      a.isInstanceOf[ParsedJSONSchema] || a.isInstanceOf[GeneratedJSONSchema] || a.isInstanceOf[JSONSchemaRoot])
    element.annotations += GeneratedJSONSchema(jsonSchema)
    jsonSchema
  }

  private def fixNameIfNeeded(element: AnyShape): AnyShape = {
    // Adding an annotation to identify the root shape of the JSON Schema
    element.annotations += JSONSchemaRoot()
    if (element.name.option().isEmpty)
      element.copyShape().withName("root")
    else {
      if (element.name.value().matches(".*/.*")) element.copyShape().withName("root")
      else element
    }
  }
}

object JsonSchemaEntry extends EntryEmitter {
  override def emit(b: EntryBuilder): Unit = b.entry("$schema", "http://json-schema.org/draft-04/schema#")

  override def position(): Position = Position.ZERO
}

case class JsonSchemaEmitter(root: AnyShape,
                             declarations: Seq[DomainElement],
                             ordering: SpecOrdering = SpecOrdering.Lexical,
                             options: ShapeRenderOptions) {
  def emitDocument(): YDocument = {
    YDocument(b => {
      b.obj { b =>
        traverse(emitters, b)
      }
    })
  }

  private val jsonSchemaRefEntry = new EntryEmitter {
    override def emit(b: EntryBuilder): Unit =
      b.entry("$ref", OasDefinitions.appendDefinitionsPrefix(root.name.value()))

    override def position(): Position = Position.ZERO
  }

  private def sortedTypeEntries =
    ordering.sorted(OasDeclarationsEmitter(declarations, SpecOrdering.Lexical, Seq())(JsonSchemaEmitterContext(
      options.errorHandler,
      options)).emitters) // spec 3 context? or 2? set from outside, from vendor?? support two versions of jsonSchema??

  private val emitters = Seq(JsonSchemaEntry, jsonSchemaRefEntry) ++ sortedTypeEntries
}
