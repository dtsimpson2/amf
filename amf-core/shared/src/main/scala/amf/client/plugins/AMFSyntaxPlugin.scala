package amf.client.plugins

import java.io.Writer

import amf.core.client.ParsingOptions
import amf.core.parser.{ParsedDocument, ParserContext}

abstract class AMFSyntaxPlugin extends AMFPlugin {
  def supportedMediaTypes(): Seq[String]
  def parse(mediaType: String,
            text: CharSequence,
            ctx: ParserContext,
            parsingOptions: ParsingOptions): Option[ParsedDocument]
  def unparse(mediaType: String, ast: ParsedDocument): Option[CharSequence]
  def unparse(mediaType: String, ast: ParsedDocument, writer: Writer): Option[Writer]
}