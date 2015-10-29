package edu.arizona.sista.reach.grounding2

import edu.arizona.sista.odin._

/**
  * Trait for simple and species-specific Mention lookup in local knowledge bases.
  *   Written by Tom Hicks. 10/28/2015.
  *   Last Modified: Add method for basic resolve of mention. Rename trait.
  */
trait KBMentionLookup extends KBLookup {

  /** Resolve the given text string to an optional entry in a knowledge base
    * Return a resolution for the entry, if any found.
    * NB: This method assumes a single resolution KB and behavior is undefined
    *     for KBs with multiple entries.
    */
  def resolve (mention:Mention): Option[KBEntry] = resolve(mention.text)

  /** Resolve the given Mention to an optional entry in a knowledge base,
    * failing if the entry is not for humans.
    * Return a resolution for the entry, if any found.
    */
  def resolveHuman (mention:Mention): Option[KBEntry] =
    resolveBySpecies(mention, HumanLabels)  // resolve for humans only

  /** Resolve the given Mention to an optional entry in a knowledge base,
    * for any of the species name strings in the given set.
    * Return a speciated resolution for the entry, if any found.
    */
  def resolveBySpecies (mention:Mention, species:SpeciesNameSet): Option[KBEntry] =
    resolveBySpecies(mention.text, species)

}

/** Trait Companion Object allows Mixin OR Import pattern. */
object KBMentionLookup extends KBMentionLookup
