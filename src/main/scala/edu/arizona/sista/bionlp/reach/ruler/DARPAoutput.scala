package edu.arizona.sista.bionlp.reach.ruler


import java.io.{PrintWriter, File, BufferedReader, FileReader}
import edu.arizona.sista.matcher.{ExtractorEngine, Mention, TextBoundMention, EventMention}
import edu.arizona.sista.bionlp.reach.core.RelationMention
import edu.arizona.sista.processors.{DocumentSerializer, Document}
import edu.arizona.sista.processors.bionlp.BioNLPProcessor

/**
 * Created by gus on 12/22/14.
 */
class DARPAoutput {
}

object DARPAoutput extends App {

  val entityRules = Ruler.readEntityRules
  val eventRules = Ruler.readEventRules
  val rules = entityRules + "\n\n" + eventRules

  val ds = new DocumentSerializer

  val actions = new DarpaActions

  val proc = new BioNLPProcessor()
  val extractor = new ExtractorEngine(rules, actions)

  //val text = "We hypothesized that the MEK / ERK pathway may suppress trans-phosphorylation of ERBB3 by directly phosphorylating the JM domains of EGFR and HER2 , and that this could be a dominant MEK inhibitor induced feedback leading to AKT activation in these cancers ."
  //val doc = proc.annotate(text)
  val outName = s"${System.getProperty("user.home")}/Desktop/DARPAout.csv"
  val output = new PrintWriter(new File(outName))

  val dir = "/Users/gus/github/reach/src/main/resources/edu/arizona/sista/bionlp/extractors/papers/"
  val papers = Seq(docFromSerializedFile(s"$dir/MEKinhibition.txt.ser"), docFromSerializedFile(s"$dir/UbiquitinationofRas.txt.ser"))
  val passageID = Map(papers.head -> 1, papers.last -> 2)

  val header = s"Pass ID;Relation;Model Link (BioPax or BEL);‘English-like’ Description;Model Representation;Source Text\n"

  println(s"Writing output to $outName")
  output.write(header)
  papers.foreach(d => retrieveMentions(d))

  def docFromSerializedFile(filename: String):Document = {
    val br = new BufferedReader(new FileReader(filename))
    val doc = ds.load(br)
    doc
  }

  def retrieveMentions(doc:Document) {
    val mentions = extractor.extractFrom(doc).filter(_.isInstanceOf[EventMention]).distinct.sortBy(m => (m.sentence, m.start))
    mentions.foreach(m => displayMention(m))
  }

  def displayMention(mention: Mention) {
    mention match {
      case m: EventMention =>
        // UNIQUE ID;RYAN's stuff;RYAN model link? (representative entities);some simple language generation here??;model representation;source text
        output.write(s"D${passageID.getOrElse(mention.document, "?")}-S${m.sentence};;;;${m.repr};${m.document.sentences(m.sentence).getSentenceText()}\n")
        //println(s"${m.label} (EventMention)")
        //println(s"trigger = ${m.trigger.text}")
        //m.arguments foreach {
        //  case (k, vs) => for (v <- vs) println(s"$k = ${v.text}")
        //}
        //println
      case _ => ()
    }
  }

  // generates a representation of the mention that can be used
  // for the csv file expected by darpa
  implicit class Repr(mention: Mention) {
    def repr: String = mention match {
      case m: TextBoundMention => s"${m.label}(${m.text})"
      case m: EventMention => s"${m.label}(${dumpArgs(m.arguments)})"
      case m: RelationMention => s"${m.label}(${dumpArgs(m.arguments)}"
    }

    private def dumpArgs(arguments: Map[String, Seq[Mention]]): String =
      arguments.map{ case (k, v) => s"$k=${dumpArgVal(v)}" }.mkString(", ")

    private def dumpArgVal(mentions: Seq[Mention]): String =
      if (mentions.size == 1) mentions(0).repr
      else s"[${mentions.map(_.repr).mkString(", ")}]"
  }
}


/***

   case m: TextBoundMention =>
        println(m.repr)
        println(s"${m.label} (TextBoundMention)")
        println(m.text)
        println
      case m: EventMention =>
        println(m.repr)
        println(s"${m.label} (EventMention)")
        println(s"trigger = ${m.trigger.text}")
        m.arguments foreach {
          case (k, vs) => for (v <- vs) println(s"$k = ${v.text}")
        }
        println
      case m: RelationMention =>
        println(s"${m.label} (RelationMention)")
        m.arguments foreach {
          case (k, vs) => for (v <- vs) println(s"$k = ${v.text}")
        }
        println
  ***/