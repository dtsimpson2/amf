Model: file://amf-client/shared/src/test/resources/validations/jsonschema/misc_shapes.raml
Profile: RAML 1.0
Conforms? false
Number of results: 1

Level: Violation

- Source: http://a.ml/vocabularies/amf/parser#exampleError
  Message:  should have required property 'emailAddresses'
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/jsonschema/misc_shapes.raml#/web-api/end-points/%2Fep1/get/200/application%2Fjson/schema/example/default-example
  Property: 
  Position: Some(LexicalInformation([(39,21)-(44,15)]))
  Location: file://amf-client/shared/src/test/resources/validations/jsonschema/misc_shapes.raml