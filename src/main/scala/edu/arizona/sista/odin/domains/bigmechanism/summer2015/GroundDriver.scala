package edu.arizona.sista.odin.domains.bigmechanism.summer2015

import java.io._

import edu.arizona.sista.processors.{DocumentSerializer, Document}
import edu.arizona.sista.processors.bionlp.BioNLPProcessor
import edu.arizona.sista.odin._
import edu.arizona.sista.bionlp.mentions._
import edu.arizona.sista.bionlp.ReachSystem

import org.slf4j.LoggerFactory

/**
  * Top-level test driver for Grounding development.
  *   Written by Tom Hicks. 4/7/2015.
  *   Last Modified: Update to use new display label.
  */
object GroundDriver extends App {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName)

  val reach = new ReachSystem
  val ds = new DocumentSerializer

  val PapersDir = s"${System.getProperty("user.dir")}/src/test/resources/inputs/papers/"
  val paperNames = Seq(
    "MEKinhibition.txt.ser",
    "UbiquitinationofRas.txt.ser",
    "PMC3441633.txt.ser",
    "PMC3847091.txt.ser"
  )

  def cleanText (m: Mention): String = {
    """(\s+|\n|\t|[;])""".r.replaceAllIn(m.document.sentences(m.sentence).getSentenceText(), " ")
  }

  def docFromSerializedFile (filename: String): Document = {
    val br = new BufferedReader(new FileReader(filename))
    val doc = ds.load(br)
    doc
  }

  def getText(fileName: String):String = scala.io.Source.fromFile(fileName).mkString

  // val outDir = s"${System.getProperty("java.io.tmpdir")}" + File.separator
  val outDir = s"${System.getProperty("user.dir")}" + File.separator
  def mkOutputName (paper:String, ext:String): String = {
    outDir + {"""^.*?/|.txt.ser""".r.replaceAllIn(paper, "")} + ext
  }

  def processPapers (papers:Seq[String]): Unit = {
    papers.foreach { paper => processPaper(paper) }
  }

  def processPaper (paper: String): Unit = {
    val outName = mkOutputName(paper, ".xref")
    val outFile = new FileOutputStream(new File(outName))
    val inFile = s"$PapersDir/$paper"

    val doc = paper match {
      case ser if ser.endsWith("ser") => docFromSerializedFile(inFile)
      case _ => reach.mkDoc(getText(inFile), "grounddriver")
    }

    val mentions = reach.extractFrom(doc)
    val sortedMentions = mentions.sortBy(m => (m.sentence, m.start)) // sort by sentence, start idx
    outputGroundedMentions(sortedMentions, doc, outFile)
  }

  /** Ground, then output the given sequence of mentions. */
  def outputGroundedMentions (mentions:Seq[Mention], doc:Document, fos:FileOutputStream): Unit = {
    val out:PrintWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(fos)))
    val ground = new LocalGrounder()
    val state = State.apply(mentions)
    val modifiedMentions = ground.apply(mentions, state)
    modifiedMentions.foreach { case m: BioMention =>
      val xref = m.xref.getOrElse("")
      out.println(s"${m.displayLabel} (${m.text}): ${xref}")
    }
    out.flush()
    out.close()
  }

  // Top-level Main of script:
  processPapers(paperNames)
}
