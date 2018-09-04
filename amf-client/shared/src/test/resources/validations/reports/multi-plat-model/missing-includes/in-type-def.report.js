Model: file://amf-client/shared/src/test/resources/validations/missing-includes/in-type-def.raml
Profile: RAML 1.0
Conforms? false
Number of results: 2

Level: Violation

- Source: http://a.ml/vocabularies/amf/parser#parsing-error
  Message: Error Loading File: java.io.IOException: ENOENT: no such file or directory, open 'amf-client/shared/src/test/resources/validations/missing-includes/conterparty.raml'
  Level: Violation
  Target: conterparty.raml
  Property: 
  Position: Some(LexicalInformation([(6,15)-(6,40)]))
  Location: file://amf-client/shared/src/test/resources/validations/missing-includes/in-type-def.raml

- Source: http://a.ml/vocabularies/amf/parser#parsing-error
  Message: Unresolved reference 'conterparty.raml' from root context file://amf-client/shared/src/test/resources/validations/missing-includes/in-type-def.raml
  Level: Violation
  Target: file://amf-client/shared/src/test/resources/validations/missing-includes/in-type-def.raml#/declarations/types/unresolved
  Property: 
  Position: Some(LexicalInformation([(6,15)-(6,40)]))
  Location: file://amf-client/shared/src/test/resources/validations/missing-includes/in-type-def.raml