package amf.domain.`abstract`

import amf.domain.extensions.DataNode
import amf.metadata.domain.`abstract`.AbstractDeclarationModel._
import amf.domain.{Annotations, DomainElement, Fields, Linkable}
import org.yaml.model.YPart

abstract class AbstractDeclaration(fields: Fields, annotations: Annotations) extends DomainElement with Linkable {

  def name: String           = fields(Name)
  def dataNode: DataNode     = fields(DataNode)
  def variables: Seq[String] = fields(Variables)

  def withName(name: String): this.type                = set(Name, name)
  def withDataNode(dataNode: DataNode): this.type      = set(DataNode, dataNode)
  def withVariables(variables: Seq[String]): this.type = set(Variables, variables)

  override def adopted(parent: String): this.type = withId(parent + "/" + name)
}

case class ResourceType(fields: Fields, annotations: Annotations) extends AbstractDeclaration(fields, annotations) {
  override def linkCopy(): ResourceType = ResourceType().withId(id)
}

object ResourceType {
  def apply(): ResourceType = apply(Annotations())

  def apply(ast: YPart): ResourceType = apply(Annotations(ast))

  def apply(annotations: Annotations): ResourceType = ResourceType(Fields(), annotations)
}

case class Trait(fields: Fields, annotations: Annotations) extends AbstractDeclaration(fields, annotations) {
  override def linkCopy(): Trait = Trait().withId(id)
}

object Trait {
  def apply(): Trait = apply(Annotations())

  def apply(ast: YPart): Trait = apply(Annotations(ast))

  def apply(annotations: Annotations): Trait = Trait(Fields(), annotations)
}
