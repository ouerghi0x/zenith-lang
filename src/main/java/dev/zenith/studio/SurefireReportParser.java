package dev.zenith.studio;

import dev.zenith.generator.trace.GenerationManifest;
import dev.zenith.generator.trace.GenerationMapping;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parser for Maven Surefire XML test execution reports (TEST-*.xml).
 * Automatically connects test failures and stack traces back to .zen schema sources
 * using the GenerationManifest from Spec #27.
 */
public class SurefireReportParser {

    public static class TestCaseResult {
        public String name;
        public String className;
        public double time;
        public String status; // PASSED, FAILED, ERROR, SKIPPED
        public String failureMessage;
        public String failureType;
        public String stackTrace;

        // Traceability Origin (Spec #27)
        public String sourceFile;
        public int sourceLine;
        public int sourceColumn;
        public String sourceSnippet;
        public String sourceContext;

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"name\": \"").append(escape(name)).append("\",");
            sb.append("\"className\": \"").append(escape(className)).append("\",");
            sb.append("\"time\": ").append(time).append(",");
            sb.append("\"status\": \"").append(escape(status)).append("\",");
            sb.append("\"failureMessage\": ").append(failureMessage != null ? "\"" + escape(failureMessage) + "\"" : "null").append(",");
            sb.append("\"failureType\": ").append(failureType != null ? "\"" + escape(failureType) + "\"" : "null").append(",");
            sb.append("\"stackTrace\": ").append(stackTrace != null ? "\"" + escape(stackTrace) + "\"" : "null").append(",");
            if (sourceFile != null && sourceLine > 0) {
                sb.append("\"sourceOrigin\": {");
                sb.append("\"file\": \"").append(escape(sourceFile)).append("\",");
                sb.append("\"line\": ").append(sourceLine).append(",");
                sb.append("\"column\": ").append(sourceColumn).append(",");
                sb.append("\"snippet\": \"").append(escape(sourceSnippet)).append("\",");
                sb.append("\"context\": \"").append(escape(sourceContext)).append("\"");
                sb.append("}");
            } else {
                sb.append("\"sourceOrigin\": null");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class TestSuiteResult {
        public String name;
        public double time;
        public int total;
        public int failures;
        public int errors;
        public int skipped;
        public final List<TestCaseResult> testCases = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"name\": \"").append(escape(name)).append("\",");
            sb.append("\"time\": ").append(time).append(",");
            sb.append("\"total\": ").append(total).append(",");
            sb.append("\"failures\": ").append(failures).append(",");
            sb.append("\"errors\": ").append(errors).append(",");
            sb.append("\"skipped\": ").append(skipped).append(",");
            sb.append("\"testCases\": [");
            for (int i = 0; i < testCases.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(testCases.get(i).toJson());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class TestRunSummary {
        public boolean running = false;
        public String status = "IDLE"; // IDLE, RUNNING, PASSED, FAILED, ERROR, STOPPED
        public double totalDuration = 0.0;
        public int totalTests = 0;
        public int passedTests = 0;
        public int failedTests = 0;
        public int skippedTests = 0;
        public final List<TestSuiteResult> suites = new ArrayList<>();
        public final List<String> rawLogs = new ArrayList<>();

        public String toJson() {
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"running\": ").append(running).append(",");
            sb.append("\"status\": \"").append(escape(status)).append("\",");
            sb.append("\"totalDuration\": ").append(totalDuration).append(",");
            sb.append("\"totalTests\": ").append(totalTests).append(",");
            sb.append("\"passedTests\": ").append(passedTests).append(",");
            sb.append("\"failedTests\": ").append(failedTests).append(",");
            sb.append("\"skippedTests\": ").append(skippedTests).append(",");
            sb.append("\"suites\": [");
            for (int i = 0; i < suites.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(suites.get(i).toJson());
            }
            sb.append("],");
            sb.append("\"rawLogs\": [");
            for (int i = 0; i < rawLogs.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(rawLogs.get(i))).append("\"");
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    /**
     * Parses all TEST-*.xml reports in the surefire reports directory and enriches
     * failure cases with AST schema origins via GenerationManifest.
     */
    public static TestRunSummary parseReports(File surefireReportsDir, GenerationManifest manifest) {
        TestRunSummary summary = new TestRunSummary();
        if (surefireReportsDir == null || !surefireReportsDir.exists() || !surefireReportsDir.isDirectory()) {
            return summary;
        }

        File[] xmlFiles = surefireReportsDir.listFiles((dir, name) -> name.startsWith("TEST-") && name.endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            return summary;
        }

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            for (File xmlFile : xmlFiles) {
                try {
                    Document doc = dBuilder.parse(xmlFile);
                    doc.getDocumentElement().normalize();
                    Element suiteElem = doc.getDocumentElement();

                    TestSuiteResult suite = new TestSuiteResult();
                    suite.name = suiteElem.getAttribute("name");
                    suite.time = parseDouble(suiteElem.getAttribute("time"));
                    suite.total = parseInt(suiteElem.getAttribute("tests"));
                    suite.failures = parseInt(suiteElem.getAttribute("failures"));
                    suite.errors = parseInt(suiteElem.getAttribute("errors"));
                    suite.skipped = parseInt(suiteElem.getAttribute("skipped"));

                    NodeList caseList = suiteElem.getElementsByTagName("testcase");
                    for (int i = 0; i < caseList.getLength(); i++) {
                        Element caseElem = (Element) caseList.item(i);
                        TestCaseResult tc = new TestCaseResult();
                        tc.name = caseElem.getAttribute("name");
                        tc.className = caseElem.getAttribute("classname");
                        tc.time = parseDouble(caseElem.getAttribute("time"));
                        tc.status = "PASSED";

                        // Check failure
                        NodeList failures = caseElem.getElementsByTagName("failure");
                        if (failures.getLength() > 0) {
                            Element f = (Element) failures.item(0);
                            tc.status = "FAILED";
                            tc.failureMessage = f.getAttribute("message");
                            tc.failureType = f.getAttribute("type");
                            tc.stackTrace = f.getTextContent();
                        }

                        // Check error
                        NodeList errors = caseElem.getElementsByTagName("error");
                        if (errors.getLength() > 0) {
                            Element e = (Element) errors.item(0);
                            tc.status = "ERROR";
                            tc.failureMessage = e.getAttribute("message");
                            tc.failureType = e.getAttribute("type");
                            tc.stackTrace = e.getTextContent();
                        }

                        // Check skipped
                        NodeList skipped = caseElem.getElementsByTagName("skipped");
                        if (skipped.getLength() > 0) {
                            tc.status = "SKIPPED";
                        }

                        // Traceability link (Spec #27)
                        if (manifest != null && (tc.status.equals("FAILED") || tc.status.equals("ERROR"))) {
                            linkTraceability(tc, manifest);
                        }

                        suite.testCases.add(tc);
                    }

                    summary.suites.add(suite);
                    summary.totalTests += suite.total;
                    summary.failedTests += (suite.failures + suite.errors);
                    summary.skippedTests += suite.skipped;
                    summary.totalDuration += suite.time;

                } catch (Exception ex) {
                    System.err.println("[Zenith SurefireReportParser] Error parsing report " + xmlFile.getName() + ": " + ex.getMessage());
                }
            }

            summary.passedTests = summary.totalTests - summary.failedTests - summary.skippedTests;
            if (summary.passedTests < 0) summary.passedTests = 0;
            summary.status = summary.failedTests > 0 ? "FAILED" : (summary.totalTests > 0 ? "PASSED" : "IDLE");

        } catch (Exception e) {
            System.err.println("[Zenith SurefireReportParser] XML Parser init failure: " + e.getMessage());
        }

        return summary;
    }

    /**
     * Resolves the source schema declaration for a failed test by parsing the test name,
     * target class, and stack trace frames through the GenerationManifest.
     */
    private static void linkTraceability(TestCaseResult tc, GenerationManifest manifest) {
        if (manifest == null) return;

        // 1. First attempt: check stack trace frames for generated source classes
        if (tc.stackTrace != null) {
            for (String line : tc.stackTrace.split("\n")) {
                line = line.trim();
                // Pattern: at com.shopsphere.backend.controller.OrderController.pay(OrderController.java:54)
                if (line.startsWith("at ") && line.contains("(") && line.contains(".java:")) {
                    try {
                        int parenOpen = line.indexOf('(');
                        int parenClose = line.indexOf(')', parenOpen);
                        if (parenOpen > 0 && parenClose > parenOpen) {
                            String fileAndLine = line.substring(parenOpen + 1, parenClose);
                            String[] parts = fileAndLine.split(":");
                            if (parts.length == 2) {
                                String fileName = parts[0].trim();
                                int lineNum = Integer.parseInt(parts[1].trim());

                                // Find mapping
                                GenerationMapping mapping = manifest.findMapping(fileName, lineNum);
                                if (mapping != null && mapping.getSourceOrigin() != null) {
                                    applyMapping(tc, mapping);
                                    return;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        // 2. Second attempt: match generated class name directly
        if (tc.className != null) {
            String targetFileName = tc.className.replace('.', '/') + ".java";
            // Check if it's a test file (e.g. OrderControllerTest -> OrderController)
            if (targetFileName.contains("Test.java")) {
                targetFileName = targetFileName.replace("Test.java", ".java");
            }
            if (targetFileName.contains("/src/test/java/")) {
                targetFileName = targetFileName.replace("/src/test/java/", "/src/main/java/");
            }

            GenerationMapping mapping = manifest.findMapping(targetFileName, 1);
            if (mapping != null && mapping.getSourceOrigin() != null) {
                applyMapping(tc, mapping);
            }
        }
    }

    private static void applyMapping(TestCaseResult tc, GenerationMapping mapping) {
        if (mapping.getSourceOrigin() != null) {
            tc.sourceFile = mapping.getSourceOrigin().getSourceFile();
            tc.sourceLine = mapping.getSourceOrigin().getStartLine();
            tc.sourceColumn = mapping.getSourceOrigin().getStartColumn();
            tc.sourceSnippet = mapping.getSnippet();
            tc.sourceContext = mapping.getGeneratedElement();
        }
    }

    private static double parseDouble(String val) {
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static int parseInt(String val) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
