package utils

import java.io.{File, FileOutputStream}
import java.util.zip.{ZipEntry, ZipFile}
import javax.xml.transform.stream.StreamSource

import com.gitb.utils.XMLUtils
import config.Configurations
import managers.TestSuiteManager
import models._
import org.apache.commons.io.{FileUtils, IOUtils}
import org.slf4j.LoggerFactory
import play.api.Play

import scala.collection.JavaConverters._
import scala.xml.XML

/**
 * Created by serbay on 10/16/14.
 */
object RepositoryUtils {
	private final val logger = LoggerFactory.getLogger("RepositoryUtils")


	val REPOSITORY_LOCATION = Configurations.TEST_CASE_REPOSITORY_PATH

	private val TEST_SUITE_ELEMENT_LABEL: String = "testsuite"

	private val TEST_CASE_ELEMENT_LABEL: String = "testcase"
	private val ACTOR_ELEMENT_LABEL: String = "actor"
	/**
	 * Extracts the test suite resources in the <code>file</code> into the <code>targetFolderName</code> folder.
	 * A folder named <code>targetFolderName</code> is created if it does not exist.
	 * @param targetFolderName
	 * @param file
	 * @return id->path maps for the test case files
	 */
	def extractTestSuiteFilesFromZipToFolder(targetFolderName: String, file: File): Map[String, String] = {
		val root: String = Configurations.TEST_CASE_REPOSITORY_PATH + "/" + TestSuiteManager.TEST_SUITES_PATH
		val application = Play.current
		val targetFolder = new File(application.getFile(root), targetFolderName)

    //target folder needs to be deleted due to an unknown exception thrown
    if(targetFolder.exists()){
      FileUtils.forceDelete(targetFolder);
    }

    logger.info("Creating folder ["+targetFolder+"]")
    targetFolder.mkdirs()

		extractTestSuiteFilesFromZipToFolder(targetFolder, file)
	}


	def undeployTestSuite(testSuiteName: String): Unit = {
		val root: String = Configurations.TEST_CASE_REPOSITORY_PATH + "/" + TestSuiteManager.TEST_SUITES_PATH
		val application = Play.current
		val targetFolder = new File(application.getFile(root), testSuiteName)

		FileUtils.deleteDirectory(targetFolder)
	}

	/**
	 * Extracts the test suite resources in the <code>file</code> into the <code>targetFolder</code>
	 * @param targetFolder
	 * @param file
	 * @return id->path maps for the test case files
	 */
	def extractTestSuiteFilesFromZipToFolder(targetFolder: File, file: File): Map[String, String] = {
		val testCasePaths = collection.mutable.HashMap[String, String]()

		if(!targetFolder.exists) {
			throw new Exception("Target folder does not exists")
		}

		if(!targetFolder.isDirectory) {
			throw new Exception("Target folder is not a directory")
		}

		if(!file.exists) {
			throw new Exception("Zip file does not exists")
		}

		val zip = new ZipFile(file)

		zip.entries().asScala.foreach {
			zipEntry =>
				val newFile = new File(targetFolder, zipEntry.getName)

				if(zipEntry.isDirectory) {
          logger.info("Creating folder ["+newFile+"]")
					newFile.mkdirs()
        } else {
          if(!newFile.exists) {
            newFile.getParentFile.mkdirs()
            newFile.createNewFile()
            logger.info("Creating new file ["+newFile+"]")

						if(isTestCase(zip, zipEntry)) {
							val testCase: com.gitb.tdl.TestCase = getTestCase(zip, zipEntry)

              logger.info("File ["+newFile+"] is a test case file")

              testCasePaths.update(testCase.getId, targetFolder.getParentFile.toURI.relativize(newFile.toURI).getPath)
            }

            val fos = new FileOutputStream(newFile, false)

            fos.write(IOUtils.toByteArray(zip.getInputStream(zipEntry)))

            fos.close()
            logger.info("Wrote ["+newFile+"]")
          } else {
            logger.info("File ["+newFile+"] is already exist")
          }
				}
		}

		testCasePaths.toMap
	}

	def getTestSuiteFromZip(specification:Long, file: File): Option[TestSuite] = {

		var result: Option[TestSuite] = None

		logger.debug("Trying to extract test suite from the file ["+file+"] - file exists: ["+file.exists+"]")

		if(file.exists) {
			val zip = new ZipFile(file)

			logger.debug("Zip file entries: " + zip.entries().asScala.map((entry) => entry.getName).mkString(", "))

			val testSuiteEntries = zip.entries().asScala.filter(isTestSuite(zip, _))

			if(testSuiteEntries.hasNext) {

				val tdlTestCases = zip.entries().asScala.filter(isTestCase(zip, _)).map(getTestCase(zip, _)).toList

				val testSuiteEntry = testSuiteEntries.next()

        logger.info("Test suite ["+testSuiteEntry.getName+"] has test cases ["+tdlTestCases.map(_.getId)+"]")

				val testSuite = {
					val tdlTestSuite: com.gitb.tdl.TestSuite = getTestSuite(zip, testSuiteEntry)

					val name: String = tdlTestSuite.getMetadata.getName
					val version: String = tdlTestSuite.getMetadata.getVersion
					val authors: String = tdlTestSuite.getMetadata.getAuthors
					val originalDate: String = tdlTestSuite.getMetadata.getPublished
					val modificationDate: String = tdlTestSuite.getMetadata.getLastModified
					val description: String = tdlTestSuite.getMetadata.getDescription

					val tdlActors = tdlTestSuite.getActors.getActor.asScala
					val tdlTestCaseEntries = tdlTestSuite.getTestcase.asScala

          logger.info("Test suite has tdlActors ["+tdlActors.map(_.getId)+"]")
          logger.info("Test suite has tdlTestCases ["+tdlTestCaseEntries.map(_.getId)+"]")

					val caseObject = TestSuites(0l, name, name, version, Option(authors), Option(originalDate), Option(modificationDate), Option(description), None, specification)

					val actors = tdlActors.map { tdlActor =>

						val endpoints = tdlActor.getEndpoint.asScala.map { tdlEndpoint => // construct actor endpoints
							val parameters = tdlEndpoint.getConfig.asScala
								.map((tdlParameter) => Parameters(0l, tdlParameter.getName, Option(tdlParameter.getDesc), tdlParameter.getUse.value(), tdlParameter.getKind.value(), 0l))
								.toList
							new Endpoint(Endpoints(0l, tdlEndpoint.getName, Option(tdlEndpoint.getDesc), 0l), parameters)
						}.toList

						new Actor(Actors(0l, tdlActor.getId, tdlActor.getName, Option(tdlActor.getDesc), 0l), endpoints)
					}.toList

					val testCases = tdlTestCaseEntries.map {
						entry =>
              logger.debug("Searching for the test case ["+entry.getId+"]")
							val tdlTestCase = tdlTestCases.find(_.getId == entry.getId).get

							logger.debug("Test case ["+tdlTestCase.getId+"] has actors ["+tdlTestCase.getActors.getActor.asScala.map(_.getId).mkString(",")+"]")
							val testCaseActors = actors.filter { actor =>
								tdlTestCase.getActors.getActor.asScala.exists((role) => role.getName == actor.actorId)
							} map(_.actorId)

							TestCases(
								0l, tdlTestCase.getId, tdlTestCase.getMetadata.getName, tdlTestCase.getMetadata.getVersion,
								Option(tdlTestCase.getMetadata.getAuthors), Option(tdlTestCase.getMetadata.getPublished),
								Option(tdlTestCase.getMetadata.getLastModified), Option(tdlTestCase.getMetadata.getDescription),
								None, tdlTestCase.getMetadata.getType.ordinal().toShort, null, 0l, Some(testCaseActors.mkString(","))
							)
					}.toList

					new TestSuite(caseObject, Some(actors), Some(testCases))
				}

				result = Some(testSuite)
			}
		}

		result
	}

	def getTestCasesFromZip(file: File): Option[List[com.gitb.tdl.TestCase]] = {
		if(file.exists) {
			val zip = new ZipFile(file)

			val tdlTestCases = zip.entries().asScala
													.filter(isTestCase(zip, _))
													.map(getTestCase(zip, _))
													.toList

			Some(tdlTestCases)
		} else {
			None
		}
	}

	private def getTestSuite(zip: ZipFile, entry: ZipEntry): com.gitb.tdl.TestSuite = {
		val stream = zip.getInputStream(entry)

		val testSuite = XMLUtils.unmarshal(classOf[com.gitb.tdl.TestSuite], new StreamSource(stream))
		testSuite
	}

	private def getTestCase(zip: ZipFile, entry: ZipEntry): com.gitb.tdl.TestCase = {
		val stream = zip.getInputStream(entry)

		val testCase = XMLUtils.unmarshal(classOf[com.gitb.tdl.TestCase], new StreamSource(stream))
		testCase
	}

	private def isTestSuite(zip: ZipFile, entry: ZipEntry): Boolean = {
		testXMLElementTagInZipEntry(zip, entry, TEST_SUITE_ELEMENT_LABEL)
	}

	private def isTestCase(zip: ZipFile, entry: ZipEntry): Boolean = {
		testXMLElementTagInZipEntry(zip, entry, TEST_CASE_ELEMENT_LABEL)
	}

	private def testXMLElementTagInZipEntry(zip: ZipFile, entry: ZipEntry, tag: String): Boolean = {
		if(!entry.isDirectory) {
			try {
				val stream = zip.getInputStream(entry)
				val xml = XML.load(stream)

				xml.label == tag
			} catch {
				case e: Exception => false
			}
		} else {
			false
		}
	}
}
