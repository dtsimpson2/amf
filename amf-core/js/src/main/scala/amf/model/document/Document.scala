package amf.model.document

import amf.core.model.document.{Document => CoreDocument}
import amf.model.domain.DomainElement

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

/**
  * JS Document model class.
  */
@JSExportAll
case class Document(private[amf] val document: CoreDocument)
    extends BaseUnit
    with EncodesModel
    with DeclaresModel {

  @JSExportTopLevel("Document")
  def this() = this(CoreDocument())

  @JSExportTopLevel("Document")
  def this(domainElement: DomainElement) = this(CoreDocument().withEncodes(domainElement.element))

  override private[amf] val element = document

}

/* TODO: to webapi plugin @modularization
@JSExportAll
class Overlay(private[amf] val overlay: CoreOverlay) extends Document(overlay) {

  def this() = this(CoreOverlay())
}

@JSExportAll
class Extension(private[amf] val extensionFragment: CoreExtension) extends Document(extensionFragment) {

  def this() = this(CoreExtension())
}
*/