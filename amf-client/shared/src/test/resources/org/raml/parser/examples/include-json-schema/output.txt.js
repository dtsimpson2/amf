Model: file://amf-client/shared/src/test/resources/org/raml/parser/examples/include-json-schema/input.raml
Profile: RAML
Conforms? false
Number of results: 2

Level: Violation

- Source: file://user.json/#/definitions/User_validation
  Message: Object at / must be valid
Scalar at //age must have data type http://www.w3.org/2001/XMLSchema#integer

  Level: Violation
  Target: file://user.json/#/definitions/User/example/bad
  Property: http://a.ml/vocabularies/data#age
  Position: Some(LexicalInformation([(12,11)-(17,0)]))
  Location: file://amf-client/shared/src/test/resources/org/raml/parser/examples/include-json-schema/input.raml

- Source: file://user.json/#/definitions/User_validation
  Message: Object at / must be valid
Scalar at //age must have data type http://www.w3.org/2001/XMLSchema#integer

  Level: Violation
  Target: file://amf-client/shared/src/test/resources/org/raml/parser/examples/include-json-schema/input.raml#/web-api/end-points/%2Fsend/post/request/application%2Fjson/schema/example/default-example
  Property: http://a.ml/vocabularies/data#age
  Position: Some(LexicalInformation([(22,17)-(27,0)]))
  Location: file://amf-client/shared/src/test/resources/org/raml/parser/examples/include-json-schema/input.raml