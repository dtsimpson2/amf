Model: file://amf-client/shared/src/test/resources/validations/annotations/annotations.raml
Profile: RAML 1.0
Conforms? false
Number of results: 1

Level: Violation

- Source: http://a.ml/vocabularies/amf/parser#exampleError
  Message:  should be integer
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/annotations/annotations.raml#/web-api/b/scalar_1
  Property: 
  Position: Some(LexicalInformation([(10,5)-(10,7)]))
  Location: file://amf-client/shared/src/test/resources/validations/annotations/annotations.raml