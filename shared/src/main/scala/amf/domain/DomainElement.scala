package amf.domain

import amf.domain.Annotation.LexicalInformation
import amf.domain.`abstract`.{ParametrizedDeclaration, ParametrizedResourceType, ParametrizedTrait}
import amf.domain.extensions.DomainExtension
import amf.metadata.Field
import amf.metadata.domain.DomainElementModel._
import amf.metadata.domain.LinkableElementModel
import amf.model.{AmfArray, AmfElement, AmfObject, AmfScalar}
import amf.vocabulary.{Namespace, ValueType}

trait Linkable extends AmfObject { this: DomainElement with Linkable =>
  var linkTarget: Option[DomainElement]    = None
  var linkAnnotations: Option[Annotations] = None

  def isLink: Boolean           = linkTarget.isDefined
  def linkLabel: Option[String] = Option(fields(LinkableElementModel.Label))

  def linkCopy(): Linkable

  def withLinkTarget(target: DomainElement): this.type = {
    linkTarget = Some(target)
    set(LinkableElementModel.TargetId, target.id)
  }

  def withLinkLabel(label: String): this.type = set(LinkableElementModel.Label, label)

  def link[T](label: String, annotations: Annotations = Annotations()): T = {
    linkCopy()
      .withLinkTarget(this)
      .withLinkLabel(label)
      .add(annotations)
      .asInstanceOf[T]
  }
}

/**
  * Internal model for any domain element
  */
trait DomainElement extends AmfObject {
  def customDomainProperties: Seq[DomainExtension] = fields(CustomDomainProperties)
  def extend: Seq[DomainElement]                   = fields(Extends)

  def withCustomDomainProperties(customProperties: Seq[DomainExtension]): this.type =
    setArray(CustomDomainProperties, customProperties)

  def withExtends(extend: Seq[DomainElement]): this.type = setArray(Extends, extend)

  def withResourceType(name: String): ParametrizedResourceType = {
    val result = ParametrizedResourceType().withName(name)
    add(Extends, result)
    result
  }

  def withTrait(name: String): ParametrizedTrait = {
    val result = ParametrizedTrait().withName(name)
    add(Extends, result)
    result
  }

  def getTypeIds(): List[String] = dynamicTypes().toList ++ `type`.map(_.iri())

  def getPropertyIds(): List[String] = fields.fields().map(f => f.field.value.iri()).toList

  def getScalarByPropertyId(propertyId: String): List[Any] = {
    fields.fields().find { f: FieldEntry =>
      f.field.value.iri() == Namespace.uri(propertyId).iri()
    } match {
      case Some(fieldEntry) =>
        fieldEntry.element match {
          case scalar: AmfScalar                    => List(scalar.value)
          case arr: AmfArray if arr.values.nonEmpty => arr.values.toList
          case _                                    => List()
        }
      case None => List()
    }
  }

  def getObjectByPropertyId(propertyId: String): Seq[DomainElement] = {
    fields.fields().find { f: FieldEntry =>
      f.field.value.iri() == Namespace.uri(propertyId).iri()
    } match {
      case Some(fieldEntry) =>
        fieldEntry.element match {
          case entity: DomainElement => List(entity)
          case arr: AmfArray if arr.values.nonEmpty && arr.values.head.isInstanceOf[DomainElement] =>
            arr.values.map(_.asInstanceOf[DomainElement]).toList
          case _ => List()
        }
      case None => List()
    }
  }

  def position(): Option[amf.parser.Range] = annotations.find(classOf[LexicalInformation]) match {
    case Some(info) => Some(info.range)
    case _          => None
  }
}

trait DynamicDomainElement extends DomainElement {
  def dynamicFields: List[Field]
  def dynamicType: List[ValueType]

  // this is used to generate the graph
  def valueForField(f: Field): Option[AmfElement]
}
