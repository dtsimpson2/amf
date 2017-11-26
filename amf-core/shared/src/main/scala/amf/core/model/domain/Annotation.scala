package amf.core.model.domain

import amf.core.registries.AMFDomainRegistry

trait Annotation

trait SerializableAnnotation extends Annotation {

  /** Extension name. */
  val name: String

  /** Value as string. */
  val value: String
}

trait AnnotationGraphLoader {
  def unparse(annotatedValue: String, objects: Map[String, AmfElement]): Annotation
}

object Annotation {
  def unapply(annotation: String): Option[(String, Map[String, AmfElement]) => Annotation] =
    AMFDomainRegistry.annotationsRegistry.get(annotation) match {
      case Some(annotationLoader) => Some(annotationLoader.unparse)
      case _                     => None
    }
}