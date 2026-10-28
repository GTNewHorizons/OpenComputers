import li.cil.oc.client.renderer.glsl.GlslDialect
import li.cil.oc.client.renderer.glsl.ProgramRecipe
import li.cil.oc.client.renderer.shading.Programs
import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.Matchers.be
import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.junit.JUnitRunner

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class ShaderSourceTest extends FunSpec {
  private val files = Programs.recipes.flatMap(recipe => Seq(recipe.name + ".vert", recipe.name + ".frag")) ++ Seq("standalone.vert", "standalone.frag")

  private val bodies = Seq("lit", "unlit")

  // Must match the names Angelica's prelude defines for a registered body.
  private val contract = Seq(
    "vert" -> "angelica_out",
    "vert" -> "angelica_modelView",
    "vert" -> "angelica_eyePosition",
    "vert" -> "angelica_clipPosition",
    "vert" -> "angelica_eyeNormal",
    "vert" -> "angelica_color",
    "vert" -> "angelica_uv",
    "vert" -> "angelica_lightmapUv",
    "vert" -> "angelica_fogCoord",
    "vert" -> "angelica_lightDirection",
    "vert" -> "angelica_lightDiffuse",
    "frag" -> "angelica_in",
    "frag" -> "angelica_FragColor",
    "frag" -> "angelica_sampleTexture",
    "frag" -> "angelica_sampleLightmap",
    "frag" -> "angelica_fog",
    "frag" -> "angelica_alphaTest")

  private val vertexContract = contract.collect { case ("vert", name) => name }.toSet

  private val fragmentContract = contract.collect { case ("frag", name) => name }.toSet

  // Any of these puts a program on Angelica's fixed-function emulation, by source rewrite or by uniform name.
  private val emulated = Seq("gl_Vertex", "gl_Color", "gl_Normal", "gl_MultiTexCoord", "gl_ModelView", "gl_Projection", "gl_NormalMatrix",
    "gl_LightSource", "gl_Fog", "gl_FragColor", "gl_TexCoord", "ftransform", "texture2D", "attribute", "varying", "iris_")

  // Angelica renames these as whole words anywhere in a source, comments included, and never renames them back.
  private val renamed = Seq("sample", "new", "sampler")

  private case class Program(recipe: ProgramRecipe, dialect: GlslDialect) {
    def shader: String = recipe.name

    def vertex: String = preprocess(recipe.assemble(dialect, read("standalone.vert"), read(shader + ".vert")))

    def fragment: String = preprocess(recipe.assemble(dialect, read("standalone.frag"), read(shader + ".frag")))

    override def toString = s"$shader ($dialect)"
  }

  private val programs = for (recipe <- Programs.recipes; dialect <- GlslDialect.values.toSeq) yield Program(recipe, dialect)

  private def read(name: String): String = {
    val stream = getClass.getResourceAsStream("/assets/opencomputers/shaders/" + name)
    if (stream == null) fail(name + " is missing")
    try Source.fromInputStream(stream, "UTF-8").mkString finally stream.close()
  }

  // Handles only #ifdef/#else/#endif, the only conditionals the sources use.
  private def preprocess(source: String): String = {
    val defined = mutable.Set.empty[String]
    var open = List.empty[Boolean]
    var taken = List.empty[Boolean]
    source.linesIterator.filter { line =>
      val words = line.trim.split("\\s+")
      val live = open.forall(identity)
      words(0) match {
        case "#ifdef" =>
          val branch = defined(words(1))
          open ::= branch
          taken ::= branch
          false
        case "#else" =>
          open = !taken.head :: open.tail
          false
        case "#endif" =>
          open = open.tail
          taken = taken.tail
          false
        case "#if" | "#ifndef" | "#elif" => fail("unsupported directive: " + line)
        case "#define" if live =>
          defined += words(1).takeWhile(_ != '(')
          true
        case _ => live
      }
    }.mkString("\n")
  }

  private def uncommented(source: String) = source.linesIterator.map(_.replaceFirst("//.*", "")).mkString("\n")

  private def angelicaNames(source: String) = "angelica_\\w*".r.findAllIn(source).toSet

  private def uniformNames(source: String) = "(?m)^\\s*uniform\\s+\\w+\\s+(\\w+)".r.findAllMatchIn(source).map(_.group(1)).toSet

  private def defined(source: String) =
    "(?m)^(?:#define |\\w+ |layout\\(location = 0\\) out vec4 )(angelica_\\w+)[ (;]".r.findAllMatchIn(source).map(_.group(1)).toSet

  describe("Assembled shader sources") {
    for (program <- programs) {
      it(s"$program declares exactly the uniforms its recipe requires") {
        (uniformNames(program.vertex) ++ uniformNames(program.fragment)) should be(program.recipe.expectedUniforms(program.dialect).asScala.toSet)
      }

      it(s"$program defines every contract name its stages use") {
        (angelicaNames(read(program.shader + ".vert")) -- defined(program.vertex)) should be(Set.empty)
        (angelicaNames(read(program.shader + ".frag")) -- defined(program.fragment)) should be(Set.empty)
      }
    }

    for (program <- programs if program.dialect == GlslDialect.GLSL330) {
      it(s"$program uses nothing Angelica would emulate") {
        val vertex = program.vertex
        val fragment = program.fragment
        for (token <- emulated) withClue(token) {
          vertex.contains(token) should be(false)
          fragment.contains(token) should be(false)
        }
        (angelicaNames(vertex) -- vertexContract) should be(Set.empty)
        (angelicaNames(fragment) -- fragmentContract) should be(Set.empty)
      }
    }
  }

  describe("Shader bodies") {
    for (shader <- bodies; (stage, contract) <- Seq("vert" -> vertexContract, "frag" -> fragmentContract)) {
      it(s"$shader.$stage is written against the contract names alone") {
        val body = uncommented(read(shader + "." + stage))
        (angelicaNames(body) -- contract) should be(Set.empty)
        body.contains("#") should be(false)
        "\\b[ua]_\\w*".r.findFirstIn(body) should be(None)
        ("\\bgl_\\w*".r.findAllIn(body).toSet - "gl_Position") should be(Set.empty)
        for (word <- Seq("uniform", "texture", "texture2D", "varying", "attribute", "layout")) withClue(word) {
          ("\\b" + word + "\\b").r.findFirstIn(body) should be(None)
        }
        "\\bvoid main\\(\\)".r.findAllIn(body).length should be(1)
      }
    }
  }

  describe("Shader source text") {
    for (file <- files) {
      it(s"$file has no word Angelica renames") {
        val source = read(file)
        for (word <- renamed) withClue(word) { ("\\b" + word + "\\b").r.findFirstIn(source) should be(None) }
      }
    }
  }
}
