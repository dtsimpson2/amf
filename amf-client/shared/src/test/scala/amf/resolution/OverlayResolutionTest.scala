package amf.resolution

import amf.core.client.GenerationOptions
import amf.core.model.document.BaseUnit
import amf.core.remote.{Raml, RamlYamlHint}
import amf.facades.AMFDumper

import scala.concurrent.ExecutionContext

class OverlayResolutionTest extends ResolutionTest {

  override implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  val basePath = "amf-client/shared/src/test/resources/resolution/overlay/"

  test("Overlay with annotations to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "annotations/")
  }

  test("Overlay with chained annotations to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "annotation-chained/")
  }

  test("Overlay with documentation to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "documentation/")
  }

  test("Overlay with examples to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "examples-mediatype/")
  }

  test("Overlay with trait annotations to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "trait-annotation/")
  }

  test("Overlay with trait displayName to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "trait-displayname/")
  }

  test("Overlay with trait empty to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "trait-empty/")
  }

  test("Overlay with types to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "types/")
  }

  test("Overlay with complex types to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "types-add/")
  }

  test("Overlay with libraries to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "with-uses/")
  }

  test("Overlay with many libraries to Raml") {
    cycle("input.raml", "output.raml", RamlYamlHint, Raml, basePath + "with-uses-both/")
  }

  override def render(unit: BaseUnit, config: CycleConfig): String = {
    val target = config.target
    new AMFDumper(unit, target, target.defaultSyntax, GenerationOptions().withSourceMaps).dumpToString
  }
}