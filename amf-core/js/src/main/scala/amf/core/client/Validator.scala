package amf.core.client

import amf.core.services.RuntimeValidator
import amf.model.document.BaseUnit

import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportAll
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportAll
object Validator {

  def validate(model: BaseUnit, profileName: String, messageStyle: String = "AMF") = RuntimeValidator(
    model.element,
    profileName,
    messageStyle
  ).toJSPromise

  def loadValidationProfile(url: String) = {
    RuntimeValidator.loadValidationProfile(url).toJSPromise
  }
}