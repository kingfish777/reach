package edu.arizona.sista.odin.extern.export.fries

import java.io._
import java.util.Date
import edu.arizona.sista.utils.DateUtils

import scala.collection.mutable.Map
import scala.collection.mutable.MutableList
import scala.collection.mutable.Set

import org.json4s._
import org.json4s.native.Serialization
import org.json4s.native.Serialization.write

import edu.arizona.sista.processors._
import edu.arizona.sista.odin._

/**
  * Defines classes and methods used to build and output FRIES models.
  *   Written by Tom Hicks. 4/30/2015.
  *   Last Modified: Memoize only root children. Begin content for Binding.
  */
class FriesOutput {
  type Memoized = scala.collection.mutable.HashSet[Mention]
  type PropMap  = scala.collection.mutable.HashMap[String, Any]
  type CardList = scala.collection.mutable.MutableList[PropMap]  // has O(c) append

  // Constants:
  val AllEvents = Set("Acetylation", "Binding", "Farnesylation",
                      "Glycosylation", "Hydrolysis", "Hydroxylation",
                      "Methylation", "Negative_regulation", "Phosphorylation",
                      "Positive_regulation", "Ribosylation", "Sumoylation",
                      "Transport", "Ubiquitination")
  val MapsToPhysicalEntity = Set("Gene_or_gene_product", "Protein",
                                 "Protein_with_site", "Simple_chemical")
  val Now = DateUtils.formatUTC(new Date())
  val RootEvents = Set("Negative_regulation", "Positive_regulation")

  // used by json output serialization:
  implicit val formats = org.json4s.DefaultFormats

  // create mention manager and cache
  protected val mentionMgr = new MentionManager()

  // the map containing values for FRIES output
  protected val fries:PropMap = new PropMap


  //
  // Public API:
  //

  /** Output a JSON object representing the FRIES output for the given mentions. */
  def toJSON (allMentions:Seq[Mention], outFile:File) = {
    val mentions = allMentions.filter(_.isInstanceOf[EventMention])
    val rootChildren = memoizeRootChildren(mentions)
    // showMemoization(rootChildren)
    val cards = new CardList
    mentions.foreach { mention =>
      if (!rootChildren.contains(mention)) {
        val card = beginNewCard(mention)
        doMention(mention, card)
        cards += card
      }
    }
    fries("cards") = cards
    writeJsonToFile(fries, outFile)
  }


  //
  // Private Methods
  //

  /** Return a new index card (map) initialized with the (repeated) document information. */
  private def beginNewCard (mention:Mention): PropMap = {
    val doc:Document = mention.document
    val card = new PropMap
    card("pmc_id") = doc.id.getOrElse("DOC-ID-MISSING")
    card("reading_started") = Now
    card("reading_ended") = Now
    card("submitter") = "UAZ"
    card("reader_type") = "machine"
    val extracted = new PropMap
    extracted("negative_information") = false
    card("extracted_information") = extracted
    return card
  }


  /** Process the given mention, adding its information to the given card. */
  private def doMention (mention:Mention, card:PropMap) = {
    mention.label match {                   // dispatch on mention type
      case "Acetylation" =>
      case "Binding" => doBinding(mention, card)
      case "Degradation" =>
      case "Exchange" =>
      case "Expression" =>
      case "Farnesylation" =>
      case "Glycosylation" =>
      case "Hydrolysis" =>
      case "Hydroxylation" =>
      case "Methylation" =>
      case "Negative_regulation" =>
      case "Phosphorylation" =>
      case "Positive_regulation" =>
      case "Ribosylation" =>
      case "Sumoylation" =>
      case "Translation" =>
      case "Transcription" =>
      case "Transport" =>
      case "Ubiquitination" =>
      case _ => ()
    }
  }

  private def doBinding (mention:Mention, card:PropMap) = {
    val themes = mentionMgr.themeArgs(mention).get
    if (!themes.isEmpty) {
      val extracted:PropMap = card.apply("extracted_information").asInstanceOf[PropMap]
      extracted("interaction_type") = "binds"
      extracted("participant_a") = makeParticipantA(themes.head, card)
      extracted("participant_b") = makeParticipantList(themes.tail, card)
      extracted("participant_a_site") = null // TODO: extract site if present
      extracted("participant_b_site") = null // TODO: extract site if present
    }
  }

  // TODO: process general text bound mention
  private def makeParticipantA (mention:Mention, card:PropMap): PropMap = {
    val part = new PropMap
    part("entity_type") = "protein"
    part("entity_text") = mention.text
    val ns = mention.xref.map(_.namespace).getOrElse("")
    val id = mention.xref.map(_.id).getOrElse("")
    part("identifier") = s"${ns}:${id}"
    return part
  }

  private def makeParticipantList (mentions:Seq[Mention], card:PropMap): Seq[PropMap] = {
    return null                             // TODO: IMPLEMENT
  }


  /** Remember all mentions reachable from the forest of root event mentions. */
  private def memoizeRootChildren (mentions:Seq[Mention]): Memoized = {
    val memoized = new Memoized
    mentions.filter(m => RootEvents.contains(m.label)).foreach{ mention =>
      for (mArg <- mention.arguments.values.flatten)
        memoizeRootChildren(mArg, memoized)
    }
    return memoized
  }

  /** Remember all descendant mentions reachable from the given mention. */
  private def memoizeRootChildren (child:Mention, memoized:Memoized): Memoized = {
    memoized += child                              // memoize this child
    for (mArg <- child.arguments.values.flatten)   // do all descendants
      memoizeRootChildren(mArg, memoized)
    return memoized
  }


  /** Print a textual representation of the memoized root mentions. REMOVE LATER? */
  private def showMemoization (rootChildren:Memoized) = {
    val sortedMentions = rootChildren.toSeq.sortBy(m => (m.sentence, m.start))
    sortedMentions.foreach { sm => mentionMgr.mentionToStrings(sm).foreach{println} }
  }


  /** Convert the entire output data structure to JSON and write it to the given file. */
  private def writeJsonToFile (fries:PropMap, outFile:File) = {
    val out:PrintWriter = new PrintWriter(new BufferedWriter(new FileWriter(outFile)))
    Serialization.writePretty(fries, out)
    out.println()                           // add final newline which serialization omits
    out.flush()
    out.close()
  }
}


/** Enumeration of values that can appear in the EntityType field. */
object EntityType extends Enumeration {
  type EntityType = Value
  val Protein, Chemical, Gene = Value
}

// Binding
// Hydrolysis
// Phosphorylation
// Positive_regulation
// Transport
