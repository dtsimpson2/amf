package amf.spec.declaration

import amf.domain.Annotation.{ExplicitField, Inferred, InlineDefinition, ParsedJSONSchema}
import amf.domain.{Annotations, CreativeWork, ExternalDomainElement, Value}
import amf.metadata.shape._
import amf.model.{AmfArray, AmfScalar}
import amf.parser.{YMapOps, YValueOps}
import amf.shape.RamlTypeDefMatcher.matchType
import amf.shape.TypeDef._
import amf.shape._
import amf.spec.Declarations
import amf.spec.common.{ArrayNode, ValueNode}
import amf.spec.raml._
import amf.vocabulary.Namespace
import org.yaml.model._
import org.yaml.parser.YamlParser

import scala.collection.mutable

object RamlTypeParser {
  def apply(ast: YMapEntry, adopt: Shape => Shape, declarations: Declarations): RamlTypeParser =
    new RamlTypeParser(ast, ast.key, ast.value, adopt, declarations)
}

trait RamlTypeSyntax {
  def parseWellKnownTypeRef(ramlType: String): Shape = {
    ramlType match {
      case "nil" | ""      => NilShape()
      case "any"           => AnyShape()
      case "string"        => ScalarShape().withDataType((Namespace.Xsd + "string").iri())
      case "integer"       => ScalarShape().withDataType((Namespace.Xsd + "integer").iri())
      case "number"        => ScalarShape().withDataType((Namespace.Xsd + "float").iri())
      case "boolean"       => ScalarShape().withDataType((Namespace.Xsd + "boolean").iri())
      case "datetime"      => ScalarShape().withDataType((Namespace.Xsd + "dateTime").iri())
      case "datetime-only" => ScalarShape().withDataType((Namespace.Xsd + "dateTime").iri())
      case "time-only"     => ScalarShape().withDataType((Namespace.Xsd + "time").iri())
      case "date-only"     => ScalarShape().withDataType((Namespace.Xsd + "date").iri())
      case "array"         => ArrayShape()
      case "object"        => NodeShape()
      case "union"         => UnionShape()
    }
  }
  def wellKnownType(str: String) =
    if (str.indexOf("|") > -1 || str.indexOf("[") > -1 || str.indexOf("{") > -1 || str.indexOf("]") > -1 || str
          .indexOf("}") > -1) {
      false
    } else {
      str match {
        case "nil" | ""      => true
        case "any"           => true
        case "string"        => true
        case "integer"       => true
        case "number"        => true
        case "boolean"       => true
        case "datetime"      => true
        case "datetime-only" => true
        case "time-only"     => true
        case "date-only"     => true
        case "array"         => true
        case "object"        => true
        case "union"         => true
        case _               => false
      }
    }
}

case class RamlTypeParser(ast: YPart, name: String, part: YNode, adopt: Shape => Shape, declarations: Declarations)
    extends RamlSpecParser
    with RamlSyntax {

  private val value = part.value

  def parse(): Option[Shape] = {

    val result = detect() match {
      case XMLSchemaType               => Some(parseXMLSchemaExpression(ast.asInstanceOf[YMapEntry]))
      case JSONSchemaType              => Some(parseJSONSchemaExpression(ast.asInstanceOf[YMapEntry]))
      case TypeExpressionType          => Some(parseTypeExpression())
      case UnionType                   => Some(parseUnionType())
      case ObjectType                  => Some(parseObjectType())
      case ArrayType                   => Some(parseArrayType())
      case AnyType                     => Some(parseAnyType())
      case typeDef if typeDef.isScalar => Some(parseScalarType(typeDef))
      case _                           => None
    }

    // Add 'inline' annotation for shape
    result
      .map(shape =>
        part.value match {
          case _: YScalar => shape.add(InlineDefinition())
          case _          => shape
      })
  }

  private def detect(): TypeDef = part.value match {
    case scalar: YScalar => matchType(scalar.text)
    case _: YSequence    => ObjectType
    case map: YMap =>
      detectItems(map)
        .orElse(detectProperties(map))
        .orElse(detectTypeOrSchema(map))
        .orElse(detectAnyOf(map))
        .getOrElse(UndefinedType)
  }

  private def detectProperties(map: YMap): Option[TypeDef] = {
    map.key("properties").map(_ => ObjectType)
  }

  private def detectItems(map: YMap): Option[TypeDef] = {
    map.key("items") match {
      case (Some(_)) => Some(ArrayType)
      case None      => None
    }
  }

  private def detectAnyOf(map: YMap): Option[TypeDef] = {
    map.key("anyOf").map(_ => UnionType)
  }

  private def detectTypeOrSchema(map: YMap) = {
    map
      .key("type")
      .orElse(map.key("schema"))
      .map(e =>
        e.value.value match {
          case scalar: YScalar =>
            val t = scalar.text
            val f = map.key("(format)").map(_.value.value.toScalar.text).getOrElse("")
            matchType(t, f)
          case _: YSequence | _: YMap => ObjectType
          case _                      => UndefinedType
      })
  }

  private def parseXMLSchemaExpression(entry: YMapEntry): Shape = {
    entry.value.value match {
      case scalar: YScalar =>
        val shape = SchemaShape().withRaw(scalar.text).withMediaType("application/xml")
        shape.withName(entry.key)
        adopt(shape)
        shape
      case map: YMap =>
        map.key("type") match {
          case Some(typeEntry: YMapEntry) if typeEntry.value.value.isInstanceOf[YScalar] =>
            val shape = SchemaShape().withRaw(typeEntry.value.value.asInstanceOf[YScalar].text).withMediaType("application/xml")
            shape.withName(entry.key)
            adopt(shape)
            shape
          case _ =>
            val shape = SchemaShape()
            adopt(shape)
            parsingErrorReport(shape.id, "Cannot parse XML Schema expression out of a non string value", Some(entry.value.value))
            shape
        }
      case _ =>
        val shape = SchemaShape()
        adopt(shape)
        parsingErrorReport(shape.id, "Cannot parse XML Schema expression out of a non string value", Some(entry.value.value))
        shape
    }
  }

  private def parseJSONSchemaExpression(entry: YMapEntry): Shape = {
    val text = entry.value.value match {
      case scalar: YScalar => scalar.text
      case map: YMap =>
        map.key("type") match {
          case Some(typeEntry: YMapEntry) if typeEntry.value.value.isInstanceOf[YScalar] =>
            typeEntry.value.value.asInstanceOf[YScalar].text
          case _ =>
            val shape = SchemaShape()
            adopt(shape)
            parsingErrorReport(shape.id, "Cannot parse XML Schema expression out of a non string value", Some(entry.value.value))
            ""
        }
      case _ =>
        val shape = SchemaShape()
        adopt(shape)
        parsingErrorReport(shape.id, "Cannot parse XML Schema expression out of a non string value", Some(entry.value.value))
        ""
    }
    val schemaAst   = YamlParser(text).parse(true)
    val schemaEntry = YMapEntry(entry.key, schemaAst.head.asInstanceOf[YDocument].node)
    OasTypeParser(schemaEntry, (shape) => adopt(shape), declarations).parse() match {
      case Some(shape) =>
        shape.annotations += ParsedJSONSchema(text)
        shape
      case None => {
        val shape = SchemaShape()
        adopt(shape)
        parsingErrorReport(shape.id, "Cannot parse JSON Schema", Some(entry))
        shape
      }
    }
  }

  private def parseTypeExpression(): Shape = {
    part.value match {
      case expression: YScalar =>
        RamlTypeExpressionParser(adopt, declarations, Some(part.value)).parse(expression.text).get

      case _: YMap => parseObjectType()
    }
  }

  private def parseScalarType(typeDef: TypeDef): Shape = {
    if (typeDef.isNil) {
      val nilShape = NilShape(ast).withName(name)
      adopt(nilShape)
      nilShape
    } else {
      val shape = ScalarShape(ast).withName(name)
      adopt(shape)
      value match {
        case map: YMap => ScalarShapeParser(typeDef, shape, map).parse()
        case v =>
          shape.set(ScalarShapeModel.DataType, AmfScalar(XsdTypeDefMapping.xsd(typeDef), Annotations(v)))
      }
    }
  }

  private def parseAnyType(): Shape = {
    val shape = AnyShape(ast).withName(name)
    adopt(shape)
    shape
  }

  def parseArrayType(): Shape = {
    val shape = value match {
      case map: YMap => DataArrangementParser(name, ast, map, (shape: Shape) => adopt(shape), declarations).parse()
      case _         => ArrayShape(ast).withName(name)
    }
    shape
  }

  private def parseUnionType(): UnionShape = {
    val shape = UnionShapeParser(value.toMap, declarations).parse()
    adopt(shape)
    shape
  }

  private def parseObjectType(): Shape = {
    if (isFileType) {
      val shape = FileShapeParser(value.toMap).parse()
      adopt(shape)
      shape
    } else {
      val shape = NodeShape(ast).withName(name)
      adopt(shape)
      value match {
        case map: YMap =>
          NodeShapeParser(shape, map, declarations)
            .parse() // I have to do the adopt before parser children shapes. Other way the children will not have the father id
        case scalar: YScalar =>
          declarations.findType(scalar.text) match {
            case Some(s) => s.link(scalar.text, Annotations(part)).asInstanceOf[Shape].withName(name)
            case _       => UnresolvedShape(scalar.text, part).withName(name)
          }
        case _ => shape
      }
    }
  }

  private def isFileType: Boolean = {

    value match {
      case map: YMap =>
        map
          .key("type")
          .exists { entry: YMapEntry =>
            entry.value.value match {
              case scalar: YScalar =>
                scalar.text == "file"
              case _ => false
            }
          }
      case _ => false
    }
  }

  trait CommonScalarParsingLogic {
    def parseOASFields(map: YMap, shape: Shape): Unit = {
      map.key("pattern", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Pattern, value.string(), Annotations(entry))
      })

      map.key("minLength", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.MinLength, value.integer(), Annotations(entry))
      })

      map.key("maxLength", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.MaxLength, value.integer(), Annotations(entry))
      })

      map.key("(exclusiveMinimum)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.ExclusiveMinimum, value.string(), Annotations(entry))
      })

      map.key("(exclusiveMaximum)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.ExclusiveMaximum, value.string(), Annotations(entry))
      })
    }
  }

  case class ScalarShapeParser(typeDef: TypeDef, shape: ScalarShape, map: YMap)
      extends ShapeParser()
      with CommonScalarParsingLogic {
    override def parse(): ScalarShape = {
      super.parse()
      parseOASFields(map, shape)
      map
        .key("type")
        .fold(shape
          .set(ScalarShapeModel.DataType, AmfScalar(XsdTypeDefMapping.xsd(typeDef)), Annotations() += Inferred()))(
          entry => shape.set(ScalarShapeModel.DataType, AmfScalar(XsdTypeDefMapping.xsd(typeDef)), Annotations(entry)))

      map.key("minimum", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Minimum, value.string(), Annotations(entry))
      })

      map.key("maximum", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Maximum, value.string(), Annotations(entry))
      })

      map.key("format", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Format, value.string(), Annotations(entry))
      })

      // We don't need to parse (format) extension because in oas must not be emitted, and in raml will be emitted.

      map.key("multipleOf", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.MultipleOf, value.integer(), Annotations(entry))
      })

      val syntaxType = Option(shape.dataType).getOrElse("#shape").split("#").last match {
        case "integer" | "float" => "numberScalarShape"
        case "string"            => "stringScalarShape"
        case _                   => "shape"
      }
      validateClosedShape(shape.id, map, syntaxType)

      shape
    }
  }

  case class UnionShapeParser(override val map: YMap, declarations: Declarations) extends ShapeParser() {
    override val shape = UnionShape(Annotations(map))

    override def parse(): UnionShape = {
      super.parse()

      map.key(
        "anyOf", { entry =>
          entry.value.value match {
            case seq: YSequence =>
              val unionNodes = seq.nodes.zipWithIndex
                .map {
                  case (node, index) =>
                    RamlTypeParser(node,
                                   s"item$index",
                                   node,
                                   item => item.adopted(shape.id + "/items/" + index),
                                   declarations).parse()
                }
                .filter(_.isDefined)
                .map(_.get)
              shape.setArray(UnionShapeModel.AnyOf, unionNodes, Annotations(seq))

            case _ =>
              parsingErrorReport(shape.id, "Unions are built from multiple shape nodes", Some(entry))
          }
        }
      )

      shape
    }
  }

  case class FileShapeParser(override val map: YMap) extends ShapeParser() with CommonScalarParsingLogic {
    override val shape = FileShape(Annotations(map))

    override def parse(): FileShape = {
      super.parse()
      parseOASFields(map, shape)

      map.key(
        "fileTypes", { entry =>
          entry.value.value match {
            case seq: YSequence =>
              val value = ArrayNode(seq)
              shape.set(FileShapeModel.FileTypes, value.strings(), Annotations(seq))
          }
        }
      )

      map.key("(minimum)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Minimum, value.string(), Annotations(entry))
      })

      map.key("(maximum)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Maximum, value.string(), Annotations(entry))
      })

      map.key("(format)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.Format, value.string(), Annotations(entry))
      })

      // We don't need to parse (format) extension because in oas must not be emitted, and in raml will be emitted.

      map.key("(multipleOf)", entry => {
        val value = ValueNode(entry.value)
        shape.set(ScalarShapeModel.MultipleOf, value.integer(), Annotations(entry))
      })

      validateClosedShape(shape.id, map, "fileShape")

      shape
    }
  }

  case class DataArrangementParser(name: String,
                                   ast: YPart,
                                   map: YMap,
                                   adopt: Shape => Unit,
                                   declarations: Declarations) {

    def lookAhead(): Either[TupleShape, ArrayShape] = {
      map.key("(tuple)") match {
        case Some(entry) =>
          entry.value.value match {
            // this is a sequence, we need to create a tuple
            case _: YSequence => Left(TupleShape(ast).withName(name))
            // not an array regular array parsing
            case _ =>
              val tuple = TupleShape(ast).withName(name)
              parsingErrorReport(tuple.id, "Tuples must have a list of types", Some(entry.value.value))
              Left(tuple)

          }
        case None => Right(ArrayShape(ast).withName(name))
      }
    }

    def parse(): Shape = {
      lookAhead() match {
        case Left(tuple)  => TupleShapeParser(tuple, map, adopt, declarations).parse()
        case Right(array) => ArrayShapeParser(array, map, adopt, declarations).parse()
      }
    }

  }

  case class ArrayShapeParser(override val shape: ArrayShape,
                              map: YMap,
                              adopt: Shape => Unit,
                              declarations: Declarations)
      extends ShapeParser() {

    override def parse(): Shape = {
      adopt(shape)

      super.parse()

      parseInheritance(declarations)

      map.key("uniqueItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.UniqueItems, value.boolean(), Annotations(entry))
      })

      val finalShape = for {
        itemsEntry <- map.key("items")
        item       <- RamlTypeParser(itemsEntry, items => items.adopted(shape.id + "/items"), declarations).parse()
      } yield {
        item match {
          case array: ArrayShape   => shape.withItems(array).toMatrixShape
          case matrix: MatrixShape => shape.withItems(matrix).toMatrixShape
          case other: Shape        => shape.withItems(other)
        }
      }

      finalShape match {
        case Some(parsed: Shape) =>
          validateClosedShape(parsed.id, map, "arrayShape")
          parsed
        case None =>
          parsingErrorReport(shape.id, "Cannot parse data arrangement shape", Some(map))
          shape
      }
    }
  }

  case class TupleShapeParser(shape: TupleShape, map: YMap, adopt: Shape => Unit, declarations: Declarations)
      extends ShapeParser() {

    override def parse(): Shape = {
      adopt(shape)

      super.parse()

      parseInheritance(declarations)

      map.key("minItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.MinItems, value.integer(), Annotations(entry))
      })

      map.key("maxItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.MaxItems, value.integer(), Annotations(entry))
      })

      map.key("uniqueItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.UniqueItems, value.boolean(), Annotations(entry))
      })

      map.key(
        "items",
        entry => {
          val items = entry.value.value.toMap.entries.zipWithIndex
            .map {
              case (elem, index) =>
                RamlTypeParser(elem, item => item.adopted(shape.id + "/items/" + index), declarations).parse()
            }
          shape.withItems(items.filter(_.isDefined).map(_.get))
        }
      )

      validateClosedShape(shape.id, map, "arrayShape")

      shape
    }
  }

  case class NodeShapeParser(shape: NodeShape, map: YMap, declarations: Declarations) extends ShapeParser() {
    override def parse(): NodeShape = {

      super.parse()

      parseInheritance(declarations)

      map.key("minProperties", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.MinProperties, value.integer(), Annotations(entry))
      })

      map.key("maxProperties", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.MaxProperties, value.integer(), Annotations(entry))
      })

      shape.set(NodeShapeModel.Closed, value = false)

      map.key("additionalProperties", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.Closed, value.negated(), Annotations(entry) += ExplicitField())
      })

      map.key("discriminator", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.Discriminator, value.string(), Annotations(entry))
      })

      map.key("discriminatorValue", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.DiscriminatorValue, value.string(), Annotations(entry))
      })

      map.key("(readOnly)", entry => {
        val value = ValueNode(entry.value)
        shape.set(NodeShapeModel.ReadOnly, value.boolean(), Annotations(entry))
      })

      map.key(
        "properties",
        entry => {
          val properties: Seq[PropertyShape] =
            PropertiesParser(entry.value.value.toMap, shape.withProperty, declarations).parse()
          shape.set(NodeShapeModel.Properties, AmfArray(properties, Annotations(entry.value)), Annotations(entry))
        }
      )

      val properties = mutable.ListMap[String, PropertyShape]()
      shape.properties.foreach(p => properties += (p.name -> p))

      map.key(
        "(dependencies)",
        entry => {
          val dependencies: Seq[PropertyDependencies] =
            ShapeDependenciesParser(entry.value.value.toMap, properties).parse()
          shape.set(NodeShapeModel.Dependencies, AmfArray(dependencies, Annotations(entry.value)), Annotations(entry))
        }
      )

      validateClosedShape(shape.id, map, "nodeShape")

      shape
    }
  }

  case class PropertiesParser(ast: YMap, producer: String => PropertyShape, declarations: Declarations) {

    def parse(): Seq[PropertyShape] = {
      ast.entries
        .map(entry => PropertyShapeParser(entry, producer, declarations).parse())
    }
  }

  case class PropertyShapeParser(entry: YMapEntry, producer: String => PropertyShape, declarations: Declarations) {

    def parse(): PropertyShape = {

      val name: String = entry.key
      val property     = producer(name).add(Annotations(entry))

      var explicitRequired: Option[Value] = None
      entry.value.value match {
        case map: YMap =>
          map.key(
            "required",
            entry => {
              val required = ValueNode(entry.value).boolean().value.asInstanceOf[Boolean]
              explicitRequired = Some(Value(AmfScalar(required), Annotations(entry) += ExplicitField()))
              property.set(PropertyShapeModel.MinCount,
                           AmfScalar(if (required) 1 else 0),
                           Annotations(entry) += ExplicitField())
            }
          )
        case _ =>
      }

      if (property.fields.?(PropertyShapeModel.MinCount).isEmpty) {
        val required = !name.endsWith("?")

        property.set(PropertyShapeModel.MinCount, if (required) 1 else 0)
        property.set(PropertyShapeModel.Name, if (required) name else name.stripSuffix("?")) // TODO property id is using a name that is not final.
      }

      property.set(PropertyShapeModel.Path, (Namespace.Data + entry.key.value.toScalar.text).iri())

      RamlTypeParser(entry, shape => shape.adopted(property.id), declarations)
        .parse()
        .foreach { range =>
          if (explicitRequired.isDefined) {
            range.fields.setWithoutId(ShapeModel.RequiredShape,
                                      explicitRequired.get.value,
                                      explicitRequired.get.annotations)
          }
          property.set(PropertyShapeModel.Range, range)
        }

      property
    }
  }

  abstract class ShapeParser() extends RamlTypeSyntax {

    val shape: Shape
    val map: YMap

    def parse(): Shape = {

      map.key("displayName", entry => {
        val value = ValueNode(entry.value)
        shape.set(ShapeModel.DisplayName, value.string(), Annotations(entry))
      })

      map.key("description", entry => {
        val value = ValueNode(entry.value)
        shape.set(ShapeModel.Description, value.string(), Annotations(entry))
      })

      map.key("default", entry => {
        val value = ValueNode(entry.value)
        shape.set(ShapeModel.Default, value.string(), Annotations(entry))
      })

      map.key("enum", entry => {
        val value = ArrayNode(entry.value.value.toSequence)
        shape.set(ShapeModel.Values, value.strings(), Annotations(entry))
      })

      map.key("minItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.MinItems, value.integer(), Annotations(entry))
      })

      map.key("maxItems", entry => {
        val value = ValueNode(entry.value)
        shape.set(ArrayShapeModel.MaxItems, value.integer(), Annotations(entry))
      })

      map.key(
        "(externalDocs)",
        entry => {
          val creativeWork: CreativeWork = OasCreativeWorkParser(entry.value.value.toMap).parse()
          shape.set(ShapeModel.Documentation, creativeWork, Annotations(entry))
        }
      )

      map.key(
        "xml",
        entry => {
          val xmlSerializer: XMLSerializer = XMLSerializerParser(shape.name, entry.value.value.toMap).parse()
          shape.set(ShapeModel.XMLSerialization, xmlSerializer, Annotations(entry))
        }
      )

      shape
    }

    def parseSchemaType(parent: String, encodes: ExternalDomainElement): Shape = {
      Option(encodes.raw) match {
        case Some(rawText) if rawText.startsWith("<") =>
          val schema: SchemaShape = SchemaShape().withRaw(rawText).withMediaType("application/xml")
          schema.adopted(parent)
          schema

        /*
        case Some(rawText) if rawText.startsWith("{") || rawText.startsWith("[") =>
          val parts = YamlParser(rawText).parse(true)
          OasTypeParser(parts.head.asInstanceOf[], (shape) => shape.adopted(parent), declarations)
         */

        case Some(rawText) =>
          val schema: SchemaShape = SchemaShape().withRaw(rawText)
          schema.adopted(parent)
          schema

        case None =>
          val schema: SchemaShape = SchemaShape()
          schema.adopted(parent)
          parsingErrorReport(schema.id, "Error, cannot parse schema type without schema text", None)
          schema
      }
    }

    protected def parseInheritance(declarations: Declarations): Unit = {
      map.key(
        "type",
        entry => {
          entry.value.value match {

            case scalar: YScalar if RamlTypeDefMatcher.TypeExpression.unapply(scalar.text).isDefined =>
              RamlTypeParser(entry, shape => shape.adopted(shape.id), declarations)
                .parse()
                .foreach(s =>
                  shape.set(NodeShapeModel.Inherits, AmfArray(Seq(s), Annotations(entry.value)), Annotations(entry)))

            case scalar: YScalar if !wellKnownType(scalar.text) =>
              // it might be a named type
              declarations.findType(scalar.text) match {
                case Some(ancestor) =>
                  shape.set(NodeShapeModel.Inherits,
                            AmfArray(Seq(ancestor), Annotations(entry.value)),
                            Annotations(entry))
                case _ =>
                  parsingErrorReport(shape.id, "Reference not found", Some(entry.value.value))
              }

            case sequence: YSequence =>
              val inherits = ArrayNode(sequence)
                .strings()
                .scalars
                .map { scalar =>
                  scalar.toString match {
                    case s if RamlTypeDefMatcher.TypeExpression.unapply(s).isDefined =>
                      RamlTypeExpressionParser(adopt, declarations).parse(s).get
                    case s if declarations.shapes.get(s).isDefined =>
                      declarations.shapes(s)
                    case s if wellKnownType(s) =>
                      parseWellKnownTypeRef(s)
                  }
                }

              shape.set(ShapeModel.Inherits, AmfArray(inherits, Annotations(entry.value)), Annotations(entry))

            case _: YMap =>
              RamlTypeParser(entry, shape => shape.adopted(shape.id), declarations)
                .parse()
                .foreach(s =>
                  shape.set(NodeShapeModel.Inherits, AmfArray(Seq(s), Annotations(entry.value)), Annotations(entry)))

            case _ =>
              shape.add(ExplicitField()) // TODO store annotation in dataType field.
          }
        }
      )
    }
  }

}
