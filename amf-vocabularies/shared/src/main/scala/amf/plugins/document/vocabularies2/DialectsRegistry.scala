package amf.plugins.document.vocabularies2

import amf.core.metamodel.{Field, Obj, Type}
import amf.core.model.document.BaseUnit
import amf.core.model.domain.{AmfObject, DomainElement}
import amf.core.parser.Annotations
import amf.core.registries.AMFDomainEntityResolver
import amf.core.remote.Context
import amf.core.services.RuntimeCompiler
import amf.core.unsafe.PlatformSecrets
import amf.core.vocabulary.ValueType
import amf.plugins.document.vocabularies2.metamodel.domain.DialectDomainElementModel
import amf.plugins.document.vocabularies2.model.document.{Dialect, DialectInstance}
import amf.plugins.document.vocabularies2.model.domain.{DialectDomainElement, NodeMapping, ObjectMapProperty}

class DialectsRegistry extends AMFDomainEntityResolver with PlatformSecrets {

  protected var map: Map[String, Dialect] = Map()

  def knowsHeader(header: String): Boolean = map.contains(headerKey(header))

  def knowsDialectInstance(instance: DialectInstance): Boolean = dialectFor(instance).isDefined

  def dialectFor(instance: DialectInstance) = {
    Option(instance.definedBy()) match {
      case Some(dialectId) => map.values.find(_.id == dialectId)
      case _               => None
    }
  }

  def allDialects(): Seq[Dialect] = map.values.toSeq

  def register(dialect: Dialect): DialectsRegistry = {
    dialect.allHeaders foreach { header => map += (header -> dialect) }
    this
  }

  def withRegisteredDialect(header:String)(k: Dialect => Option[BaseUnit]): Option[BaseUnit] = {
    map.get(headerKey(header.split("\\|").head)) match {
      case Some(dialect) => k(dialect)
      case _ => None
    }
  }

  protected def headerKey(header: String) = header.trim.replace(" ", "")

  override def findType(typeString: String): Option[Obj] = {
    val foundMapping: Option[(Dialect, DomainElement)] = map.values.collectFirst {
      case dialect: Dialect =>
        dialect.declares.find {
          case nodeMapping: NodeMapping => nodeMapping.nodetypeMapping == typeString
          case _                        => false
        } map { nodeMapping =>
          (dialect, nodeMapping)
        }
    }.flatten

    foundMapping match {
      case Some((dialect: Dialect, nodeMapping: NodeMapping)) =>
        Some(buildMetaModel(nodeMapping, dialect))
      case _ => None
    }
  }

  override def buildType(modelType: Obj): Option[Annotations => AmfObject] = modelType match {
    case dialectModel: DialectDomainElementModel =>
      val reviver = (annotations: Annotations) =>
        dialectModel.nodeMapping match {
          case Some(nodeMapping) =>
            DialectDomainElement(annotations)
              .withInstanceTypes(Seq(dialectModel.typeIri, nodeMapping.id))
              .withDefinedBy(nodeMapping)
          case _ =>
            throw new Exception(s"Cannot find node mapping for dialectModel $dialectModel")
        }

      Some(reviver)
    case _ => None
  }

  def buildMetaModel(nodeMapping: NodeMapping, dialect: Dialect): DialectDomainElementModel = {
    val nodeType = nodeMapping.nodetypeMapping
    val fields = nodeMapping.propertiesMapping().map(_.toField())
    val mapPropertiesInDomain = dialect.declares.collect { case nodeMapping: NodeMapping =>
      nodeMapping.propertiesMapping().filter(_.classification() == ObjectMapProperty)
    }.flatten.filter(prop => Option(prop.objectRange()).getOrElse(Nil).contains(nodeMapping.id))

    val mapPropertiesFields = mapPropertiesInDomain.map(_.mapKeyProperty()).distinct.map( iri => Field(Type.Str, ValueType(iri)))

    new DialectDomainElementModel(nodeType, fields ++ mapPropertiesFields, Some(nodeMapping))
  }
}
