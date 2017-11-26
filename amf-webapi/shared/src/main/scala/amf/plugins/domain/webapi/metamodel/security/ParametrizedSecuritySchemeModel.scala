package amf.plugins.domain.webapi.metamodel.security

import amf.core.metamodel.Field
import amf.core.metamodel.Type.{Iri, Str}
import amf.core.metamodel.domain.DomainElementModel
import amf.core.metamodel.domain.templates.KeyField
import amf.plugins.domain.webapi.models.security.ParametrizedSecurityScheme
import amf.core.vocabulary.Namespace.Security
import amf.core.vocabulary.ValueType

object ParametrizedSecuritySchemeModel extends DomainElementModel with KeyField {

  val Name = Field(Str, Security + "name")

  val Scheme = Field(Iri, Security + "scheme")

  val Settings = Field(SettingsModel, Security + "settings")

  override val key: Field = Name

  override def fields: List[Field] = List(Name, Scheme, Settings) ++ DomainElementModel.fields

  override val `type`: List[ValueType] = List(Security + "ParametrizedSecurityScheme") ++ DomainElementModel.`type`

  override def modelInstance = ParametrizedSecurityScheme()
}