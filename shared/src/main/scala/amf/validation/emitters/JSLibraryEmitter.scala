package amf.validation.emitters

import amf.validation.model.ValidationSpecification
import amf.vocabulary.Namespace

object JSLibraryEmitter {
  val preamble: String =
    s"""
      |function amfExtractLiteral(v){
      |    if(v.datatype == null || v.datatype.value === "http://www.w3.org/2001/XMLSchema#string") {
      |        return v.value;
      |    } else if(v.datatype.value === "http://www.w3.org/2001/XMLSchema#integer") {
      |        return parseInt(v.value);
      |    } else if(v.datatype.value === "http://www.w3.org/2001/XMLSchema#float") {
      |        return parseFloat(v.value);
      |    } else if(v.datatype.value === "http://www.w3.org/2001/XMLSchema#boolean") {
      |        return v.value === "true";
      |    } else {
      |        return value;
      |    }
      |}
      |
      |function amfCompactProperty(prop) {
      |    var prefixes = $prefixes;
      |    for (var p in prefixes) {
      |        if (prop.indexOf(prefixes[p]) === 0) {
      |            return p + ":" + prop.replace(prefixes[p], "")
      |        }
      |    }
      |
      |    return prop;
      |}
      |
      |function amfFindNode(node, cache) {
      |    var acc = {"@id": amfCompactProperty(node.value)};
      |    var pairs = $$data.query().match(node, "?p", "?o");
      |    cache[node.value] = acc;
      |    for(var pair = pairs.nextSolution(); pair; pair = pairs.nextSolution()) {
      |        var prop = amfCompactProperty(pair.p.value);
      |        if (pair.p.value === "http://www.w3.org/1999/02/22-rdf-syntax-ns#type") {
      |            prop = "@type"
      |        }
      |
      |        var value = acc[prop] || [];
      |        acc[prop] = value;
      |        if(prop === "@type") {
      |            value.push(amfCompactProperty(pair.o.value));
      |        } else if(pair.o.termType === "BlankNode" || pair.o.value.indexOf("urn:") === 0 ) {
      |            value.push(cache[pair.o.value] || amfFindNode(pair.o, cache));
      |        } else if (pair.o.value.indexOf("http:/") === 0 || pair.o.value.indexOf("file:/") === 0) {
      |            value.push(cache[pair.o.value] || amfFindNode(pair.o, cache));
      |        } else {
      |            value.push(amfExtractLiteral(pair.o));
      |        }
      |    }
      |
      |    return acc;
      |}
      |if (typeof(console) === "undefined") {
      |  console = {
      |    log: function(x) { print(x) }
      |  };
      |}
    """.stripMargin

  def prefixes: String = {
    "{\n" + Namespace.ns.map { case (prefix, ns) => "  \"" + prefix + "\": \"" + ns.base + "\"" }.mkString(",\n") + "}"
  }
}

class JSLibraryEmitter {

  /**
    * Emit the JS library that will wrap custom JS validations to access the model elements.
    *
    * @param validations
    * @return JSON-LD graph with the validations
    */
  def emitJS(validations: Seq[ValidationSpecification]): Option[String] = {
    val constraints = for {
      validation         <- validations
      functionConstraint <- validation.functionConstraint
      _                  <- functionConstraint.code
    } yield {
      validation
    }

    if (constraints.isEmpty) {
      None
    } else {
      Some(composeText(constraints))
    }
  }

  def composeText(validations: Seq[ValidationSpecification]): String = {
    var text = JSLibraryEmitter.preamble

    validations.foreach { (validation) =>
      val constraint = validation.functionConstraint.get

      text +=
        s"""
        |
        |function ${constraint.computeFunctionName(validation.id())}($$this, ${constraint.validatorArgument(
             validation.id())}) {
        |  var innerFn = ${constraint.code.get};
        |  var input = amfFindNode($$this, {});
        |  // print(JSON.stringify(input))
        |  return innerFn(input);
        |}
        |
      """.stripMargin
    }

    text
  }

}
