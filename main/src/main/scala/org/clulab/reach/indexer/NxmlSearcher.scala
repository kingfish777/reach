package org.clulab.reach.indexer

import java.io.{FileWriter, PrintWriter, File}
import java.nio.file.Paths
import org.clulab.processors.bionlp.BioNLPProcessor
import org.clulab.utils.StringUtils
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.{TopScoreDocCollector, IndexSearcher}
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import scala.collection.mutable
import NxmlSearcher._
import scala.collection.mutable.ArrayBuffer


/**
 * Searches the NXML index created by NXML indexer
 * User: mihais
 * Date: 10/19/15
 */
class NxmlSearcher(val indexDir:String) {
  val reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)))
  val searcher = new IndexSearcher(reader)
  val proc = new BioNLPProcessor(withChunks = false)

  def close() = reader.close()

  def docs(ids:Set[(Int, Float)]):Set[(Document, Float)] = {
    val ds = new mutable.HashSet[(Document, Float)]()
    for(id <- ids) {
      ds += new Tuple2(searcher.doc(id._1), id._2)
    }
    ds.toSet
  }

  def saveIds(docs:Set[(Document, Float)]): Unit = {
    val os = new PrintWriter(new FileWriter("ids.txt"))
    for(doc <- docs) {
      val id = doc._1.get("id")
      os.println(id)
    }
    os.close()
  }

  def saveNxml(resultDir:String, docs:Set[(Document, Float)], howManyToSave:Int = 0): Unit = {
    val docSeq = if (howManyToSave > 0) {
      docs.toSeq.sortBy(-_._2).take(howManyToSave)
    } else {
      docs.toSeq.sortBy(-_._2)
    }
    val sos = new PrintWriter(new FileWriter(resultDir + File.separator + "scores.tsv"))
    for(doc <- docSeq) {
      val id = doc._1.get("id")
      val nxml = doc._1.get("nxml")
      val os = new PrintWriter(new FileWriter(resultDir + File.separator + id + ".nxml"))
      os.print(nxml)
      os.close()
      sos.println(s"$id\t${doc._2}")
    }
    sos.close()
  }

  def saveDocs(resultDir:String, docIds:Set[(Int, Float)]): Unit = {
    val sos = new PrintWriter(new FileWriter(resultDir + File.separator + "scores.tsv"))
    var count = 0
    for(docId <- docIds) {
      val doc = searcher.doc(docId._1)
      val id = doc.get("id")
      val nxml = doc.get("nxml")
      val year = doc.get("year")
      val size = nxml.toString.length * 2 // in bytes
      val os = new PrintWriter(new FileWriter(resultDir + File.separator + id + ".nxml"))
      os.print(nxml)
      os.close()
      sos.println(s"$id\t${docId._2}\t$year\t$size")
      count += 1
    }
    sos.close()
    logger.info(s"Saved $count documents.")
  }

  def search(query:String, totalHits:Int = TOTAL_HITS):Set[(Int, Float)] = {
    searchByField(query, "text", new StandardAnalyzer(), totalHits)
  }

  def searchId(id:String, totalHits:Int = 1):Set[(Int, Float)] = {
    searchByField(id, "id", new WhitespaceAnalyzer(), totalHits)
  }

  def searchByField(query:String,
                    field:String,
                    analyzer:Analyzer,
                    totalHits:Int = TOTAL_HITS,
                    verbose:Boolean = true):Set[(Int, Float)] = {
    val q = new QueryParser(field, analyzer).parse(query)
    val collector = TopScoreDocCollector.create(totalHits)
    searcher.search(q, collector)
    val hits = collector.topDocs().scoreDocs
    val results = new mutable.HashSet[(Int, Float)]
    for(hit <- hits) {
      val docId = hit.doc
      val score = hit.score
      results += new Tuple2(docId, score)
    }
    if(verbose) logger.debug(s"""Found ${results.size} results for query "$query"""")
    results.toSet
  }

  def intersection(s1:Set[(Int, Float)], s2:Set[(Int, Float)]):Set[(Int, Float)] = {
    val result = new mutable.HashSet[(Int, Float)]()
    for(s <- s1) {
      var found = false
      var otherScore = 0.0.toFloat
      for(o <- s2 if ! found) {
        if(s._1 == o._1) {
          found = true
          otherScore = o._2
        }
      }
      if(found) {
        result += new Tuple2(s._1, s._2 + otherScore)
      }
    }
    result.toSet
  }

  def union(s1:Set[Int], s2:Set[Int]):Set[Int] = {
    val result = new mutable.HashSet[Int]()
    s1.foreach(result += _)
    s2.foreach(result += _)
    result.toSet
  }

  def countDocsContaining(eventDocs:Set[(Int, Float)], token:String):Int = {
    // token could be a phrase; make sure quotes are used
    val query = s"""Ras AND "$token""""
    val result = intersection(eventDocs, search(query))
    result.size
  }

  /** Use case for Natasa, before the Jan 2016 PI meeting */
  def useCase(resultDir:String): Unit = {
    val eventDocs = search("phosphorylation phosphorylates ubiquitination ubiquitinates hydroxylation hydroxylates sumoylation sumoylates glycosylation glycosylates acetylation acetylates farnesylation farnesylates ribosylation ribosylates methylation methylates binding binds")
    val result = intersection(eventDocs, search("""Ras AND (ROS OR "antioxidant response element" OR Warburg OR MAPK OR "Raf/Mek/Erk" OR Akt OR NfkB OR TGFb OR TGFbeta OR TGFb1 OR TGFbeta1 OR integrins OR ADAM OR EGF OR EGFR OR RTK OR apoptosis OR autophagy OR proliferation OR "transcription factors" OR ATM OR p53 OR RB OR "tumor suppressors" OR glycolysis OR "pentose phosphate pathway" OR OXPHOS OR mitochondria OR "cell cycle" OR "energy balance" OR exosomes OR RAGE OR HMGB1)"""))
    logger.debug(s"The result contains ${result.size} documents.")
    val resultDocs = docs(result)
    saveNxml(resultDir, resultDocs, 0)
    saveIds(resultDocs)

    //
    // histogram of term distribution in docs
    //

    logger.debug("Generating topic histogram...")
    val histoPoints = Array(
      "ROS",
      "antioxidant response element",
      "Warburg",
      "MAPK",
      "Raf/Mek/Erk",
      "Akt",
      "NfkB",
      "TGFb",
      "TGFbeta",
      "TGFb1",
      "TGFbeta1",
      "integrins",
      "ADAM",
      "EGF",
      "EGFR",
      "EGFR",
      "RTK",
      "apoptosis",
      "autophagy",
      "proliferation",
      "transcription factors",
      "ATM",
      "p53",
      "RB",
      "tumor suppressors",
      "glycolysis",
      "pentose phosphate pathway",
      "exosomes",
      "OXPHOS",
      "mitochondria",
      "cell cycle",
      "energy balance",
      "RAGE",
      "HMGB1")

    val histoValues = new ArrayBuffer[(String, Int)]()
    for(point <- histoPoints) {
      histoValues += new Tuple2(point, countDocsContaining(result, point))
    }
    val histoFile = new PrintWriter(new FileWriter(resultDir + File.separator + "histo.txt"))
    for(i <- histoValues.sortBy(0 - _._2)) {
      histoFile.println(s"${i._1}\t${i._2}")
    }
    histoFile.close()


    logger.debug("Done.")
  }

  def vanillaUseCase(query:String, resultDir:String) {
    val eventDocs = search(query)
    logger.debug(s"The result contains ${eventDocs.size} documents.")
    saveDocs(resultDir, eventDocs)
    logger.debug("Done.")
  }

  /** Finds all NXML that contain at least one biochemical interaction */
  def useCase2(resultDir:String) {
    vanillaUseCase(
      "phosphorylation phosphorylates ubiquitination ubiquitinates hydroxylation hydroxylates sumoylation sumoylates glycosylation glycosylates acetylation acetylates farnesylation farnesylates ribosylation ribosylates methylation methylates binding binds",
      resultDir)
  }

  /** Use case for childrens health */
  def useCaseCH(resultDir:String): Unit = {
    vanillaUseCase(
      "children AND ((TNFAlpha AND nutrition) OR (inflammation AND stunting) OR (kcal AND inflammation) OR (protein AND inflammation) OR (nutrition AND inflammation))",
      resultDir)
  }

  def useCaseTB(resultDir:String): Unit = {
    vanillaUseCase(
      """ "chronic inflammation" AND ("tissue damage" OR "tissue repair" OR "wound healing" OR "angiogenesis" OR "fibrosis" OR "resolvin" OR "eicosanoid" OR "tumor-infiltrating lymphocyte" OR "lymphoid aggregate" OR "granuloma" OR "microbiome" OR "short-chain fatty acid") """,
      resultDir)
  }

  //
  // 4 queries for an older use case for Natasa
  //
  // Natasa's use case, first query
  def useCase4a(resultDir:String): Unit = {
    vanillaUseCase("""(TGFbeta1 OR "Transforming Growth Factor beta 1") AND (BMP OR "Bone Morphogenetic Protein")""", resultDir)
  }
  // Natasa's use case, second query
  def useCase4b(resultDir:String): Unit = {
    vanillaUseCase("""(TGFbeta1 OR "Transforming Growth Factor beta 1") AND pancreas""", resultDir)
  }
  // Natasa's use case, third query
  def useCase4c(resultDir:String): Unit = {
    vanillaUseCase("""(BMP OR "Bone Morphogenetic Protein") AND pancreas""", resultDir)
  }
  // Natasa's use case, fourth query
  def useCase4d(resultDir:String): Unit = {
    vanillaUseCase("""(TGFbeta1 OR "Transforming Growth Factor beta 1") AND (BMP OR "Bone Morphogenetic Protein") AND pancreas""", resultDir)
  }

  /** Dengue use case */
  def useCaseDengue(resultDir:String): Unit = {
    vanillaUseCase(
      """(Dengue OR den OR denv OR Dengue-1 Dengue-2 OR Dengue-3 OR Dengue-4 OR Dengue1 Dengue2 OR Dengue3 OR Dengue4 OR Den-1 OR Den-2 OR Den-3 OR Den-4 OR Den1 OR Den2 OR Den3 OR Den4 OR Denv1 OR Denv2 OR Denv3 OR Denv4 Denv-1 OR Denv-2 OR Denv-3 OR Denv-4) AND (serotype OR serotypes OR viremia OR "capillary leakage" OR "hemorrhagic fever" OR "self-limited dengue fever" OR fever OR "dengue shock syndrome" OR "inapparent dengue infection" OR "serial infection" OR "homologous response" OR "heterologous response" OR "immune evasion" OR "arthropod borne" OR mosquito OR mosquitoes OR "mosquito-borne" OR prm OR ns1 OR ns2a OR ns2b OR ns3 OR ns4a OR ns4 OR ns5)""",
      resultDir)
  }

  //
  // Phase III evaluation use cases
  //
  /** Phase III CMU use case (a) */
  def useCasePhase3a(resultDir:String): Unit = {
    // vanillaUseCase(s"""($GAB2) AND ($AKT OR $BETA_CATENIN OR $PAI1 OR $GRB2)""", resultDir)
    vanillaUseCase(s"""($GAB2) AND ($AKT OR $BETA_CATENIN OR $PAI1 OR $GRB2) AND ($PANCREAS)""", resultDir)
  }
  /** Phase III CMU use case (b) */
  def useCasePhase3b(resultDir:String): Unit = {
    //vanillaUseCase(s"""($MEK) AND ($ERK OR $AKT OR $PHASE3_DRUG)""", resultDir)
    //vanillaUseCase(s"""($MEK) AND ($ERK OR $AKT OR $PHASE3_DRUG) AND $PANCREAS""", resultDir)
    //vanillaUseCase(s"""($MEK) AND $PHASE3_DRUG AND ($PANCREAS)""", resultDir) // v1
    vanillaUseCase(s"""($MEK) AND ($PHASE3_DRUG OR $AKT) AND ($PANCREAS)""", resultDir) // v3
  }
  /** Phase III CMU use case (c) 3/1/2017 */
  def useCasePhase3c(resultDir:String): Unit = {
    // SEARCH 1 GAB2
    //vanillaUseCase(s"""($GAB2) AND (phosphatidylinositol OR proliferation OR SHC1 OR PI3K OR PIK3 OR $GRB2 OR PTPN11 OR SFN OR YWHAH OR HCK OR AKT OR $BETA_CATENIN OR Calcineurin OR SERPINE1) NOT "Fc-epsilon receptor" NOT osteoclast NOT "mast cell"""", resultDir)

    // SEARCH 2 catenin
    //vanillaUseCase(s"""$BETA_CATENIN AND (Wnt OR AXIN1 OR AXIN2 OR AXIN OR APC OR CSNK1A1 OR GSK3B OR TCF OR LEF OR TCF\\/LEF OR CDK2 OR PTPN6 OR CCEACAM1 OR insulin OR PML OR RANBP2 OR YAP1 OR GSK3 OR HSPB8 OR SERPINE1 OR AKT OR PTPN13 OR ACAP1 OR MST1R) NOT neuroblasts NOT neurogenesis NOT anoikis NOT cardiac NOT EMT NOT breast NOT embryonic NOT osteoblast NOT synapse NOT muscle NOT renal""", resultDir)

    // SEARCH 3 MEK inh
    //vanillaUseCase(s"""(Pancreas OR PDAC OR "pancreatic cancer") AND ($MEK OR "MEK inhibitor" OR "MEK inhibition" OR Trametinib OR Selumetinib OR Pimasertib OR PD184352 OR PD318088 OR PD0325901 OR AZD6244 OR AZD6300 OR TAK-733) AND ($AKT) AND ($ERK OR Ki67 OR RB)""", resultDir)

    // SEARCH 3 alternative
    vanillaUseCase(s"""(Pancreas OR PDAC OR "pancreatic ductal adenocarcinoma" OR "pancreatic cancer") AND ($MEK OR "MEK inhibitor" OR "MEK inhibition" OR Trametinib OR Selumetinib OR Pimasertib OR PD184352 OR PD318088 OR PD0325901 OR AZD6244 OR AZD6300 OR TAK-733) AND ($AKT OR $ERK OR Ki67 OR RB)""", resultDir)
  }

  /** Phase III May 2017 CMU/UPitt */
  def useCasePhase3d(resultDir:String): Unit = {
    // query partial
    vanillaUseCase(s"""melanoma AND (p70S6K OR S6 OR gsk OR gsk3 OR gsk3a OR gsk3b OR src OR 4ebp1 OR "eIF4E\\-binding protein 1" OR PHASI OR ybi OR YBX1 OR NSEP1 OR YB1 OR CRD OR Y\\-box OR SkMel\\-133)""", resultDir)
  }

  /** Use case for neuro cognitive development */
  def useCaseNCD(resultDir:String): Unit = {
    vanillaUseCase(
      "(children OR fetal OR prenatal OR neonatal OR infant OR childhood) AND (neuro OR cognitive OR early) AND (development OR ECD) AND measure",
      resultDir)
  }

  def searchByIds(ids:Array[String], resultDir:String): Unit = {
    val result = new mutable.HashSet[(Int, Float)]()
    logger.info(s"Searching for ${ids.length} ids: ${ids.mkString(", ")}")
    for(id <- ids) {
      val docs = searchId(id)
      if(docs.isEmpty) {
        logger.info(s"Found 0 results for id $id!")
      } else if(docs.size > 1) {
        logger.info(s"Found ${docs.size} for id $id, which should not happen!")
      } else {
        result ++= docs
      }
    }
    logger.info(s"Found ${result.size} documents for ${ids.length} ids.")
    val resultDocs = docs(result.toSet)

    saveNxml(resultDir, resultDocs)
    saveIds(resultDocs)
  }
}

object NxmlSearcher {
  val logger = LoggerFactory.getLogger(classOf[NxmlSearcher])
  val TOTAL_HITS = 500000

  // necessary for Phase III queries
  // FIXME: removed "ERK1\/2" from ERK, "AKT1\/2" from AKT, "MEK1\/2" from MEK. Too many false positives. Why?
  val ERK = """ERK OR ERK1 OR MK03 OR MAPK3 OR ERK2 OR MK01 OR MAPK1 OR "mitogen\-activated protein kinase 3" OR "mitogen\-activated protein kinase 1""""
  val MEK = """MEK OR MEK1 OR MP2K1 OR MAP2K1 OR MEK2 OR MP2K2 OR MAP2K1 OR "dual specificity mitogen\-activated protein kinase kinase 1" OR "dual specificity mitogen\-activated protein kinase kinase 2""""
  val AKT = """AKT OR AKT1 OR AKT2 OR "rac\-alpha serine\/threonine\-protein kinase" OR "rac-beta serine\/threonine\-protein kinase""""
  val GAB2 = """GAB2 OR "grb2\-associated\-binding protein 2""""
  val BETA_CATENIN = """beta\-catenin OR B\-catenin OR "catenin beta\-1" OR ctnnb1"""
  val PAI1 = """PAI1 OR PAI\-1 OR "PAI 1" OR "plasminogen activator inhibitor 1""""
  val PANCREAS = """pancreas OR pancreatic"""
  val GRB2 = """GRB2 OR "growth factor receptor\-bound protein 2""""
  val PHASE3_DRUG = """AZD6244"""

  def main(args:Array[String]): Unit = {
    val props = StringUtils.argsToProperties(args)
    val indexDir = props.getProperty("index")
    val resultDir = props.getProperty("output")
    val searcher = new NxmlSearcher(indexDir)

    if(props.containsKey("ids")) {
      val ids = readIds(props.getProperty("ids"))
      searcher.searchByIds(ids, resultDir)
    } else {
      //searcher.useCase(resultDir)
      searcher.useCasePhase3d(resultDir)
      //searcher.useCaseNCD(resultDir)
    }

    searcher.close()
  }

  def readIds(fn:String):Array[String] = {
    val ids = new ArrayBuffer[String]()
    for(line <- io.Source.fromFile(fn).getLines()) {
      var l = line.trim
      if (! l.startsWith("PMC"))
        l = "PMC" + l
      ids += l
    }
    ids.toArray
  }
}
