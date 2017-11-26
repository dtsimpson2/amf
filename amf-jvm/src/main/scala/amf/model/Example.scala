package amf.model

import amf.model.domain.Example
import amf.plugins.domain.shapes.models

/**
  * Jvm example model class
  */
case class Example private[model] (private val example: models.Example) extends DomainElement with Linkable {

  val name: String        = element.name
  val displayName: String = element.displayName
  val description: String = element.description
  val value: String       = element.value
  val strict: Boolean     = element.strict
  val mediaType: String   = element.mediaType

  def withName(name: String): this.type = {
    example.withName(name)
    this
  }
  def withDisplayName(displayName: String): this.type = {
    example.withDisplayName(displayName)
    this
  }
  def withDescription(description: String): this.type = {
    example.withDescription(description)
    this
  }
  def withValue(value: String): this.type = {
    example.withValue(value)
    this
  }
  def withStrict(strict: Boolean): this.type = {
    example.withStrict(strict)
    this
  }
  def withMediaType(mediaType: String): this.type = {
    example.withMediaType(mediaType)
    this
  }

  override private[amf] def element = example

  override def linkTarget: Option[DomainElement with Linkable] =
    element.linkTarget.map({ case l: models.Example => domain.Example(l) })

  override def linkCopy(): DomainElement with Linkable = Example(element.linkCopy())
}
