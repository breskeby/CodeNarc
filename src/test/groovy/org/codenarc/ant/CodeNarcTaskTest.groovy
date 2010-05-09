/*
 * Copyright 2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codenarc.ant

import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Project
import org.apache.tools.ant.types.FileSet
import org.codenarc.report.HtmlReportWriter
import org.codenarc.test.AbstractTestCase
import org.codenarc.analyzer.SourceAnalyzer
import org.codenarc.ruleset.RuleSet
import org.codenarc.results.Results
import org.codenarc.results.FileResults
import org.codenarc.report.XmlReportWriter

/**
 * Tests for the CodeNarc Ant Task
 *
 * @author Chris Mair
 * @version $Revision$ - $Date$
 */
class CodeNarcTaskTest extends AbstractTestCase {

    private static final BASE_DIR = 'src/test/resources'
    private static final RULESET_FILE = 'rulesets/RuleSet1.xml'
    private static final RULESET_FILES = 'rulesets/RuleSet1.xml,rulesets/RuleSet2.xml'
    private static final HTML_REPORT_FILE = 'CodeNarcTaskHtmlReport.html'
    private static final XML_REPORT_FILE = 'CodeNarcTaskXmlReport.xml'
    private static final RESULTS = new FileResults('path', [])

    private codeNarcTask
    private fileSet
    private project

    void testMaxViolationsDefaultViolations() {
        assert codeNarcTask.maxPriority1Violations == Integer.MAX_VALUE
        assert codeNarcTask.maxPriority2Violations == Integer.MAX_VALUE
        assert codeNarcTask.maxPriority3Violations == Integer.MAX_VALUE
    }

    void testExecute_MaxPriority1Violations() {
        codeNarcTask.maxPriority1Violations = 10
        testMaxViolations(1, 13)
    }

    void testExecute_MaxPriority2Violations() {
        codeNarcTask.maxPriority2Violations = 10
        testMaxViolations(2, 12)
    }

    void testExecute_MaxPriority3Violations() {
        codeNarcTask.maxPriority3Violations = 10
        testMaxViolations(3, 11)
    }

    private void testMaxViolations(int priority, int numViolations) {
        codeNarcTask.addFileset(fileSet)
        def reportWriter = [ writeReport: {ctx, results -> } ]
        codeNarcTask.reportWriters = [reportWriter]
        StubSourceAnalyzerCategory.reset()
        StubSourceAnalyzerCategory.violationCounts[priority] = numViolations
        use(StubSourceAnalyzerCategory) {
            def errorMessage = shouldFail(BuildException) { codeNarcTask.execute() }
            log("errorMessage=$errorMessage")
            assert errorMessage.contains("p${priority}=${numViolations}")
        }
    }

    void testExecute_CodeNarcProperties() {
        def analysisContext = null
        def reportWriter = [ writeReport: {ctx, results -> analysisContext = ctx} ]
        codeNarcTask.reportWriters = [reportWriter]
        codeNarcTask.addFileset(fileSet)
        codeNarcTask.execute()
        log("rules=${analysisContext.ruleSet.rules}")
        assert analysisContext.ruleSet.rules[0].priority == 3
    }

    void testExecute_SingleRuleSetFile() {
        def codeNarcRunner = createAndUseFakeCodeNarcRunner()

        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE))
        codeNarcTask.addFileset(fileSet)
        codeNarcTask.execute()

        assert codeNarcRunner.sourceAnalyzer.class == AntFileSetSourceAnalyzer
        assert codeNarcRunner.ruleSetFiles == RULESET_FILE
        assertStandardHtmlReportWriter(codeNarcRunner)
    }

    void testExecute_TwoRuleSetFiles() {
        def codeNarcRunner = createAndUseFakeCodeNarcRunner()

        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE))
        codeNarcTask.ruleSetFiles = RULESET_FILES
        codeNarcTask.addFileset(fileSet)
        codeNarcTask.execute()

        assert codeNarcRunner.sourceAnalyzer.class == AntFileSetSourceAnalyzer
        assert codeNarcRunner.ruleSetFiles == RULESET_FILES
        assertStandardHtmlReportWriter(codeNarcRunner)
    }

    void testExecute_TwoFileSets() {
        def codeNarcRunner = createAndUseFakeCodeNarcRunner()
        def fileSet2 = new FileSet(dir:new File('/abc'), project:project)

        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE))
        codeNarcTask.addFileset(fileSet)
        codeNarcTask.addFileset(fileSet2)
        codeNarcTask.execute()

        assert codeNarcRunner.sourceAnalyzer.class == AntFileSetSourceAnalyzer
        assert codeNarcRunner.sourceAnalyzer.fileSets == [fileSet, fileSet2]
        assert codeNarcRunner.ruleSetFiles == RULESET_FILE
        assertStandardHtmlReportWriter(codeNarcRunner)
    }

    void testExecute_RuleSetFileDoesNotExist() {
        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE))
        codeNarcTask.ruleSetFiles = 'DoesNotExist.xml'
        codeNarcTask.addFileset(fileSet)
        shouldFailWithMessageContaining('DoesNotExist.xml') { codeNarcTask.execute() }
    }

    void testExecute_NullRuleSetFiles() {
        codeNarcTask.ruleSetFiles = null
        shouldFailWithMessageContaining('ruleSetFile') { codeNarcTask.execute() }
    }

    void testExecute_NullFileSet() {
        shouldFailWithMessageContaining('fileSet') { codeNarcTask.execute() }
    }

    void testAddConfiguredReport() {
        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE))
        assert codeNarcTask.reportWriters.size() == 1
        assert codeNarcTask.reportWriters[0].class == HtmlReportWriter
        assert codeNarcTask.reportWriters[0].outputFile == HTML_REPORT_FILE
        assert codeNarcTask.reportWriters[0].title == null
    }

    void testAddConfiguredReport_Second() {
        codeNarcTask.addConfiguredReport(new Report(type:'xml', toFile:XML_REPORT_FILE))
        codeNarcTask.addConfiguredReport(new Report(type:'html', toFile:HTML_REPORT_FILE, title:'ABC'))
        assert codeNarcTask.reportWriters.size() == 2
        assert codeNarcTask.reportWriters[0].class == XmlReportWriter
        assert codeNarcTask.reportWriters[0].outputFile == XML_REPORT_FILE
        assert codeNarcTask.reportWriters[1].class == HtmlReportWriter
        assert codeNarcTask.reportWriters[1].outputFile == HTML_REPORT_FILE
        assert codeNarcTask.reportWriters[1].title == 'ABC'
    }

    void testAddConfiguredReport_SpecifyReportWriterClassname() {
        codeNarcTask.addConfiguredReport(new Report(type:'org.codenarc.report.HtmlReportWriter', toFile:HTML_REPORT_FILE))
        assert codeNarcTask.reportWriters.size() == 1
        assert codeNarcTask.reportWriters[0].class == HtmlReportWriter
        assert codeNarcTask.reportWriters[0].outputFile == HTML_REPORT_FILE
        assert codeNarcTask.reportWriters[0].title == null
    }

    void testAddConfiguredReport_NullToFile() {
        codeNarcTask.addConfiguredReport(new Report(type:'html', title:'ABC'))
        assert codeNarcTask.reportWriters.size() == 1
        assert codeNarcTask.reportWriters[0].title == 'ABC'
        assert codeNarcTask.reportWriters[0].outputFile == null
    }

    void testAddConfiguredReport_InvalidReportType() {
        shouldFailWithMessageContaining('XXX') { codeNarcTask.addConfiguredReport(new Report(type:'XXX', toFile:HTML_REPORT_FILE)) }
    }

    void testAddConfiguredReport_ReportOptionsSetPropertiesOnReportWriter() {
        def report = createReport("html", [title:'abc', outputFile:'def'])
        codeNarcTask.addConfiguredReport(report)
        log(codeNarcTask.reportWriters)
        assert codeNarcTask.reportWriters[0].title == 'abc'
        assert codeNarcTask.reportWriters[0].outputFile == 'def'
    }

    void testAddFileSet_Null() {
        shouldFailWithMessageContaining('fileSet') { codeNarcTask.addFileset(null) }
    }

    void setUp() {
        super.setUp()

        project = new Project(basedir:'.')
        fileSet = new FileSet(dir:new File(BASE_DIR), project:project)
        fileSet.setIncludes('sourcewithdirs/**/*.groovy')

        codeNarcTask = new CodeNarcTask(project:project)
        codeNarcTask.ruleSetFiles = RULESET_FILE
    }

    private createAndUseFakeCodeNarcRunner() {
        def codeNarcRunner = [execute: { return RESULTS }]
        codeNarcTask.createCodeNarcRunner = { return codeNarcRunner }
        return codeNarcRunner
    }

    private void assertStandardHtmlReportWriter(codeNarcRunner) {
        assert codeNarcRunner.reportWriters.size == 1
        def reportWriter = codeNarcRunner.reportWriters[0]
        assert reportWriter.class == HtmlReportWriter
        assert reportWriter.outputFile == HTML_REPORT_FILE
    }

    private Report createReport(String type, Map options=null) {
        def report = new Report(type:type)
        options?.each { name, value ->
            report.addConfiguredOption(new ReportOption(name:name, value:value))
        }
        return report
    }
}

class StubSourceAnalyzerCategory {
    static violationCounts

    static void reset() { violationCounts = [1:0, 2:0, 3:0] }
    
    static SourceAnalyzer createSourceAnalyzer(CodeNarcTask self) {
        def results = [getNumberOfViolationsWithPriority:{ p, r -> return violationCounts[p]}] as Results
        return [analyze:{RuleSet ruleSet -> return results}, getSourceDirectories:{[]}] as SourceAnalyzer
    }
}