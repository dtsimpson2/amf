package amf.plugins.document.vocabularies

import amf.core.Root
import amf.core.client.GenerationOptions
import amf.core.metamodel.Obj
import amf.core.model.document._
import amf.core.model.domain.AmfObject
import amf.core.parser.{Annotations, ParserContext}
import amf.core.plugins.{AMFDocumentPlugin, AMFValidationPlugin}
import amf.core.registries.AMFDomainEntityResolver
import amf.core.remote.Platform
import amf.core.services.RuntimeValidator
import amf.core.validation._
import amf.core.validation.core.ValidationProfile
import amf.plugins.document.vocabularies.metamodel.document.DialectNodeFragmentModel
import amf.plugins.document.vocabularies.model.document.DialectFragment
import amf.plugins.document.vocabularies.model.domain.DomainEntity
import amf.plugins.document.vocabularies.references.RAMLExtensionsReferenceCollector
import amf.plugins.document.vocabularies.registries.PlatformDialectRegistry
import amf.plugins.document.vocabularies.spec.{DialectContext, DialectEmitter, DialectNode, DialectParser}
import amf.plugins.document.vocabularies.validation.AMFDialectValidations
import org.yaml.model.{YComment, YDocument}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait RamlHeaderExtractor {
  def comment(root: Root): Option[YComment] = root.parsed.comment

  def comment(document: YDocument): Option[YComment] =
    document.children.find(v => v.isInstanceOf[YComment]).asInstanceOf[Option[YComment]]
}

object DialectHeader extends RamlHeaderExtractor {
  def apply(root: Root): Boolean = comment(root) match {
    case Some(comment: YComment) => comment.metaText match {
      case t if t.startsWith("%RAML 1.0 Vocabulary") => true
      case t if t.startsWith("%RAML 1.0 Dialect")    => true
      case t if t.startsWith("%RAML 1.0")            => false
      case t if t.startsWith("%RAML 0.8")            => false
      case t if t.startsWith("%")                    => true
      case _                                         => false
    }
    case _                                           => false
  }
}
object RAMLVocabulariesPlugin extends AMFDocumentPlugin with AMFValidationPlugin with ValidationResultProcessor with RamlHeaderExtractor {

  override val ID = "RAML Vocabularies"

  val vendors = Seq("RAML Vocabularies", "RAML 1.0")

  override def serializableAnnotations() = Map.empty


  override def parse(root: Root, parentContext: ParserContext, platform: Platform): Option[BaseUnit] = {
    implicit val ctx: DialectContext = new DialectContext(parentContext)
    comment(root) match {
      case Some(comment: YComment) =>
        val header = comment.metaText
        if (PlatformDialectRegistry.knowsHeader(header)) {
          Some(DialectParser(root, header, PlatformDialectRegistry).parseUnit())
        } else {
          None
        }
      case _ => None
    }
  }

  override def canUnparse(unit: BaseUnit) = unit match {
    case document: Document => document.encodes.isInstanceOf[DomainEntity]
    case module: Module =>
      module.declares exists {
        case _: DomainEntity => true
        case _               => false
      }
    case _: DialectFragment => true
    case _                  => false
  }

  def canParse(root: Root): Boolean = DialectHeader(root)

  override def unparse(unit: BaseUnit, options: GenerationOptions) = Some(DialectEmitter(unit).emit())

  override def modelEntities = Seq(DialectNodeFragmentModel)

  // We plug-in the logic to rebuild serialised domain entities
  override def modelEntitiesResolver: Option[AMFDomainEntityResolver] = Some(
    new AMFDomainEntityResolver {
      override def buildType(modelType: Obj): Option[Annotations => AmfObject] =
        modelType match {
          case dialectType: DialectNode => Some({ (annotations: Annotations) =>
            DomainEntity(dialectType, annotations)
          })
          case _ => None
        }

      override def findType(typeString: String): Option[Obj] = PlatformDialectRegistry.knowsType(typeString)
    }
  )

  override def documentSyntaxes = Seq(
    "application/raml",
    "application/raml+json",
    "application/raml+yaml",
    "text/yaml",
    "text/x-yaml",
    "application/yaml",
    "application/x-yaml"
  )

  override def referenceCollector() = new RAMLExtensionsReferenceCollector()

  /**
    * Validation profiles supported by this plugin by default
    */
  override def domainValidationProfiles(platform: Platform): Map[String, () => ValidationProfile] = {
    PlatformDialectRegistry.dialects.foldLeft(Map[String, () => ValidationProfile]()) {
      case (acc, dialect) if dialect.name.indexOf("Validation Profile") == -1 => acc.updated(dialect.name, () => new AMFDialectValidations(dialect).profile())
      case (acc, _) => acc
    }
  }

  /**
    * Request for validation of a particular model, profile and list of effective validations form that profile
    */
  override def validationRequest(baseUnit: BaseUnit, profile: String, validations: EffectiveValidations, platform: Platform): Future[AMFValidationReport] = {
    var aggregatedReport: List[AMFValidationResult] = List()

    for {
      shaclReport <- RuntimeValidator.shaclValidation(baseUnit, validations)
    } yield {

      // aggregating parser-side validations
      var results = aggregatedReport.map(r => processAggregatedResult(r, "RAML", validations))

      // adding model-side validations
      results ++= shaclReport.results
        .map(r => buildValidationResult(baseUnit, r, "RAML", validations))
        .filter(_.isDefined)
        .map(_.get)

      AMFValidationReport(
        conforms = !results.exists(_.level == SeverityLevels.VIOLATION),
        model = baseUnit.id,
        profile = profile,
        results = results
      )
    }
  }

  override def dependencies() = Seq()

  /**
    * Resolves the provided base unit model, according to the semantics of the domain of the document
    */
  override def resolve(unit: BaseUnit) = unit // we don't support resolutino in vocabularies yet
}