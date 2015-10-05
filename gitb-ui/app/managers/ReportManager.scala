package managers

import java.io.{StringReader, FileOutputStream, File}
import java.math.BigInteger
import java.text.SimpleDateFormat
import javax.xml.bind.JAXBElement
import javax.xml.transform.stream.StreamSource

import com.gitb.core.{AnyContent, StepStatus}
import com.gitb.tbs.{ObjectFactory, TestStepStatus}
import com.gitb.tpl.TestCase
import com.gitb.tr._
import com.gitb.utils.{XMLDateTimeUtils, XMLUtils}
import config.Configurations
import models.{TestResultReport, TestStepResult, TestResult}
import org.apache.commons.codec.binary.Base64
import org.apache.poi.xwpf.usermodel._
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth
import org.slf4j.{LoggerFactory, Logger}
import persistence.db.PersistenceSchema
import play.api.Play

import play.api.libs.concurrent.Execution.Implicits._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.slick.driver.MySQLDriver.simple._
import utils.{JacksonUtil, TimeUtil}
import scala.collection.JavaConversions._

/**
 * Created by senan on 03.12.2014.
 */
object ReportManager extends BaseManager {

	val STATUS_UPDATES_PATH: String = "status-updates"
	val logger: Logger = LoggerFactory.getLogger("ReportManager")

  def getTestResults(systemId:Long, _page: Option[Long] = None, _limit:Option[Long] = None): Future[List[TestResultReport]] = {
    Future{
      DB.withSession { implicit session =>
	      val page = _page match {
		      case Some(p) => p
		      case None => 0l
	      }

	      val limit = _limit match {
		      case Some(l) => l
		      case None => 100
	      }

	      logger.debug("Returning last executed test results for page: ["+page+"] and limit: ["+limit+"]")

        val testResults = PersistenceSchema.testResults
	        .filter(_.sutId === systemId)
	        .drop(page * limit)
	        .take(limit)
	        .sortBy(_.startTime.desc)
	        .list

	      testResults map { testResult =>
		      val testCase = PersistenceSchema.testCases
			      .filter(_.id === testResult.testCaseId)
		        .firstOption

		      val actor = PersistenceSchema.actors
		        .filter(_.id === testResult.actorId)
		        .firstOption

		      TestResultReport(testResult, testCase, actor)
	      }
      }
    }
  }

  def getTestResultOfSession(sessionId:String): Future[TestResult] = {
    Future {
      DB.withSession{ implicit session =>
        val testResult = PersistenceSchema.testResults.filter(_.testSessionId === sessionId).first
        val xml = testResult.tpl
        val testcase = XMLUtils.unmarshal(classOf[TestCase], new StreamSource(new StringReader(xml)))
        val json = JacksonUtil.serializeTestCasePresentation(testcase)
        testResult.withPresentation(json)
      }
    }
  }

  def createTestReport(sessionId:String, systemId:Long, testCaseName:String, actorId:Long, presentation:String):Future[Unit] = {
    Future{
      DB.withSession { implicit session =>
        val initialStatus = TestResultType.UNDEFINED.value()
        val startTime     = TimeUtil.getCurrentTime()

	      val testCaseId = {
		      val testCaseOptionId = PersistenceSchema.testCases
			      .filter(_.shortname === testCaseName)
		        .map(_.id)
		        .firstOption

		      testCaseOptionId match {
			      case Some(id) => id
			      case None => -1
		      }
	      }

        PersistenceSchema.testResults.insert(TestResult(sessionId, systemId, actorId, testCaseId, initialStatus, startTime, None, None, presentation))
      }
    }
  }

  def finishTestReport(sessionId:String, status:TestResultType):Future[Unit] = {
    Future{
      DB.withSession { implicit  session =>
        val q = for {t <- PersistenceSchema.testResults if t.testSessionId === sessionId} yield ( t.result, t.endTime )
        q.update(status.value(), Some(TimeUtil.getCurrentTime()))
      }
    }
  }

	def createTestStepReport(sessionId:String, step:TestStepStatus):Future[Unit] = {
    Future{
      DB.withSession { implicit session =>
        //save status reports only when step is concluded with either COMPLETED or ERROR state
        if (step.getReport != null && (step.getStatus == StepStatus.COMPLETED || step.getStatus == StepStatus.ERROR)) {
          step.getReport.setId(step.getStepId)
          val path = sessionId + "/" + step.getStepId + ".xml"

          //write the report into a file
          if(step.getReport != null) {
            val file = new File(Play.current.getFile(Configurations.TEST_CASE_REPOSITORY_PATH + "/" + STATUS_UPDATES_PATH), path)
            file.getParentFile.mkdirs()
            file.createNewFile()

            val stream = new FileOutputStream(file)
            stream.write(XMLUtils.marshalToString(new ObjectFactory().createUpdateStatusRequest(step)).getBytes)
            stream.close()
          }
          //save the path of the report file to the DB
          val result = TestStepResult(sessionId, step.getStepId, step.getStatus.ordinal().toShort , path)
          PersistenceSchema.testStepReports.insert(result)
        }
      }
    }
  }

  def getTestStepResults(sessionId:String): Future[List[TestStepResult]] = {
    Future{
      DB.withSession { implicit session =>
        PersistenceSchema.testStepReports.filter(_.testSessionId === sessionId).list
      }
    }
  }

  def generateTestCaseReport(list:ListBuffer[TestStepReportType], path:String): File = {
    val doc = new XWPFDocument

    for(stepReport <- list) {
      generateTestStepDocument(doc, stepReport)

      //add page break
      val paragraph = doc.createParagraph();
      val run = paragraph.createRun()
      run.addBreak(BreakType.PAGE)
    }

    val out = new FileOutputStream(path);
    doc.write(out);

    out.close()

    new File(path)
  }

  def generateTestStepReport(report: TestStepReportType, path:String): File = {
    var doc = generateTestStepDocument(null, report)

    val out = new FileOutputStream(path);
    doc.write(out);

    new File(path)
  }

  private def generateTestStepDocument(document:XWPFDocument, report: TestStepReportType): XWPFDocument = {
    var doc = document

    if(doc == null)
      doc = new XWPFDocument()

    //header
    val header = doc.createParagraph();
    header.setAlignment(ParagraphAlignment.LEFT);
    header.setBorderBottom(Borders.SINGLE);

    val headerText = header.createRun();
    headerText.setBold(true)
    headerText.setFontSize(18)
    headerText.setText("Step " + report.getId + "")

    //empty
    lineBreak(doc)

    //result
    val result = doc.createParagraph();
    result.setAlignment(ParagraphAlignment.LEFT);

    val r1 = result.createRun();
    r1.setBold(true)
    r1.setFontSize(13)
    r1.setText("Result: ");

    val r2 = result.createRun();
    r2.setFontSize(13)
    r2.setText(report.getResult.value())

    lineBreak(doc)

    //time
    val time = doc.createParagraph();
    time.setAlignment(ParagraphAlignment.LEFT);

    val r3 = time.createRun();
    r3.setBold(true)
    r3.setFontSize(12)
    r3.setText("Time: ");

    val r4 = time.createRun();
    r4.setFontSize(12)
    val calendar = report.getDate.toGregorianCalendar();
    val formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm")
    formatter.setTimeZone(calendar.getTimeZone());
    r4.setText(formatter.format(calendar.getTime()))

    lineBreak(doc)

    if(report.isInstanceOf[TAR]) {
      val tar = report.asInstanceOf[TAR]

      //values
      if(tar.getContext != null && tar.getContext.getItem != null && tar.getContext.getItem.size() > 0) {
        val values = doc.createParagraph();
        values.setAlignment(ParagraphAlignment.LEFT);

        val valuesText = values.createRun();
        valuesText.setBold(true)
        valuesText.setFontSize(12)
        valuesText.setText("Values:")
        valuesText.addBreak()

        for (item <- tar.getContext.getItem) {
          val table = doc.createTable(1, 1);

          val width = table.getCTTbl().addNewTblPr().addNewTblW();
          width.setType(STTblWidth.DXA);
          width.setW(BigInteger.valueOf(9072));

          if(item.getType == "map") {
            val p = table.getRow(0).getCell(0).getParagraphs.get(0)

            val r1 = p.createRun();
            r1.setBold(true);
            r1.setText(item.getName);
            r1.addBreak()
            r1.addBreak()

            println(item.getItem.size())

            for(inner <- item.getItem) {
              if(inner.getEmbeddingMethod().value() == "STRING") {
                writeItem(p, inner, false, 1, 2)
              }

              else if(inner.getEmbeddingMethod().value() == "BASE64" || inner.getEmbeddingMethod().value() == "BASE_64"){
                writeItem(p, inner, true, 1, 2)
              }
            }
          }
          else if(item.getEmbeddingMethod().value() == "STRING") {
            val p = table.getRow(0).getCell(0).getParagraphs.get(0)
            writeItem(p, item, false, 2, 0)
          }

          else if(item.getEmbeddingMethod().value() == "BASE64" || item.getEmbeddingMethod().value() == "BASE_64"){
            val p = table.getRow(0).getCell(0).getParagraphs.get(0)
            writeItem(p, item, true, 2, 0)
          }

          lineBreak(doc)
        }
      }

      //assertions
      if (tar.getReports != null && tar.getReports.getInfoOrWarningOrError.size() > 0) {
        //empty
        lineBreak(doc)

        //assertions
        val assertions = doc.createParagraph();
        assertions.setAlignment(ParagraphAlignment.LEFT);

        val assertionsText = assertions.createRun();
        assertionsText.setBold(true)
        assertionsText.setFontSize(12)
        assertionsText.setText("Assertions:")
        assertionsText.addBreak()

        for (assertion <- tar.getReports.getInfoOrWarningOrError) {
          val bar = assertion.getValue.asInstanceOf[BAR]

          val table = doc.createTable(1, 1);

          val width = table.getCTTbl().addNewTblPr().addNewTblW();
          width.setType(STTblWidth.DXA);
          width.setW(BigInteger.valueOf(9072));

          val p = table.getRow(0).getCell(0).getParagraphs.get(0)

          val r1 = p.createRun();
          r1.setBold(true);
          r1.setText("Type: ");

          val r2 = p.createRun();
          val _type = assertion.getName().getLocalPart()
          r2.setText((_type.charAt(0) + "").toUpperCase() + _type.substring(1));
          r2.addBreak()

          val r3 = p.createRun();
          r3.setBold(true);
          r3.setText("Description: ");

          val r4 = p.createRun();
          if (bar.getDescription.startsWith("\n"))
            r4.setText(bar.getDescription().substring(1));
          else
            r4.setText(bar.getDescription());
          r4.addBreak()

          val r5 = p.createRun();
          r5.setBold(true);
          r5.setText("Test: ");

          val r6 = p.createRun();
          r6.setText(bar.getTest())

          lineBreak(doc)
        }
      }
    }
    else if(report.isInstanceOf[DR]){
      val dr = report.asInstanceOf[DR]

      val decision = doc.createParagraph();
      decision.setAlignment(ParagraphAlignment.LEFT);

      val r1 = result.createRun();
      r1.setBold(true)
      r1.setFontSize(13)
      r1.setText("Decision: ");

      val r2 = result.createRun();
      r2.setFontSize(13)
      val value = if(dr.isDecision) "true" else "false"
      r2.setText(value)

      lineBreak(doc)
    }

    doc
  }

  private def writeItem(paragraph: XWPFParagraph, item: AnyContent, base64:Boolean, break1:Int, break2: Int) = {
    val r1 = paragraph.createRun();
    r1.setBold(true);
    r1.setText(item.getName);
    var i=0;
    for(i <- 1 to break1){
      r1.addBreak()
    }

    val r2 = paragraph.createRun();
    if(base64) {
      r2.setText(new String(Base64.decodeBase64(item.getValue.getBytes)));
    } else {
      r2.setText(item.getValue);
    }

    for(i <- 1 to break2){
      r2.addBreak()
    }
  }

  private def lineBreak(doc: XWPFDocument) = {
    val empty = doc.createParagraph();
    val emptyText = empty.createRun();
    emptyText.setText("\n")
  }
}
