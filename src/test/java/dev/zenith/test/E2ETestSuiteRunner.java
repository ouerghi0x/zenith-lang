package dev.zenith.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * E2ETestSuiteRunner: Main test orchestrator and diagnostic reporter.
 * Executes Tiers 1-4 opaque-box tests for Zenith Feature #1 (Action Variable Binding).
 */
public class E2ETestSuiteRunner {

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    public static void main(String[] args) {
        int selectedTier = -1;
        int selectedFeature = 1;
        for (int i = 0; i < args.length; i++) {
            if ("--tier".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    selectedTier = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {}
            } else if ("--feature".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    selectedFeature = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (selectedFeature == 2) {
            runFeature2(selectedTier);
            return;
        }
        if (selectedFeature == 5) {
            runFeature5(selectedTier);
            return;
        }
        if (selectedFeature == 10) {
            runFeature10(selectedTier);
            return;
        }
        if (selectedFeature == 13) {
            runFeature13(selectedTier);
            return;
        }

        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        System.out.println(BOLD + CYAN + "    Zenith Feature #1: Action Variable Binding (var x = create / update)         " + RESET);
        System.out.println(BOLD + CYAN + "    Opaque-Box E2E Test Suite (Tiers 1 - 4)                                     " + RESET);
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);

        long suiteStart = System.currentTimeMillis();
        List<TestHarness.TestResult> results = new ArrayList<>();

        if (selectedTier == -1 || selectedTier == 1) {
            runTier(1, "Tier 1: Feature Coverage (Parsing, AST, Type Inference, Transpilation, @Transactional)",
                    Tier1FeatureCoverageTest.class, results);
        }
        if (selectedTier == -1 || selectedTier == 2) {
            runTier(2, "Tier 2: Boundary & Corner Cases (Duplicates, Reserved Words, Self-References, Errors)",
                    Tier2BoundaryNegativeTest.class, results);
        }
        if (selectedTier == -1 || selectedTier == 3) {
            runTier(3, "Tier 3: Cross-Feature Combinations (Chaining, Guards, Interleaved Actions)",
                    Tier3CombinationsTest.class, results);
        }
        if (selectedTier == -1 || selectedTier == 4) {
            runTier(4, "Tier 4: Real-World Application Scenarios (E-Commerce, Multi-Tenant, Banking)",
                    Tier4RealWorldScenariosTest.class, results);
        }

        long totalDuration = System.currentTimeMillis() - suiteStart;
        printSummary(results, totalDuration);

        boolean allPassed = results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println(RED + "\n[FAILURE] Some test cases did not pass. Check diagnostics above." + RESET);
            System.exit(1);
        } else {
            System.out.println(GREEN + "\n[SUCCESS] All " + results.size() + " test cases passed cleanly!" + RESET);
            System.exit(0);
        }
    }

    /** Feature #2 (Database Indexes): all tiers live in one class, grouped by the T&lt;n&gt;_ prefix. */
    private static void runFeature2(int selectedTier) {
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        System.out.println(BOLD + CYAN + "    Zenith Feature #2: Database Indexes (@index / @uniqueIndex)                 " + RESET);
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        long start = System.currentTimeMillis();
        List<TestHarness.TestResult> results = new ArrayList<>();
        String[] titles = {
                "Tier 1: Parsing (inline, composite, names, unique)",
                "Tier 2: Semantic Validation (unknown fields, duplicates, types, names)",
                "Tier 3: Flyway SQL Generation (dialects, naming, truncation, quoting)",
                "Tier 4: JPA Entity Generation (@Table indexes)" };
        for (int t = 1; t <= 4; t++) {
            if (selectedTier == -1 || selectedTier == t) {
                runTier(t, titles[t - 1], Feature2DatabaseIndexesTest.class, results, "^T" + t + "_.*");
            }
        }
        printSummary(results, System.currentTimeMillis() - start);
        boolean allPassed = results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println(RED + "\n[FAILURE] Some test cases did not pass. Check diagnostics above." + RESET);
            System.exit(1);
        }
        System.out.println(GREEN + "\n[SUCCESS] All " + results.size() + " test cases passed cleanly!" + RESET);
        System.exit(0);
    }

    /** Feature #5 (Transactional Email Service): all tiers live in Feature5TransactionalEmailTest. */
    private static void runFeature5(int selectedTier) {
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        System.out.println(BOLD + CYAN + "    Zenith Feature #5: Transactional Email Service (mail / send)               " + RESET);
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        long start = System.currentTimeMillis();
        List<TestHarness.TestResult> results = new ArrayList<>();
        String[] titles = {
                "Tier 1: DSL Parsing & AST Modeling",
                "Tier 2: Semantic Validation & Boundaries",
                "Tier 3: Asynchronous Spring Mail & Thymeleaf Generator",
                "Tier 4: Service & Action Block Integration" };
        for (int t = 1; t <= 4; t++) {
            if (selectedTier == -1 || selectedTier == t) {
                runTier(t, titles[t - 1], Feature5TransactionalEmailTest.class, results, "^T" + t + "_.*");
            }
        }
        printSummary(results, System.currentTimeMillis() - start);
        boolean allPassed = results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println(RED + "\n[FAILURE] Some test cases did not pass. Check diagnostics above." + RESET);
            System.exit(1);
        }
        System.out.println(GREEN + "\n[SUCCESS] All " + results.size() + " test cases passed cleanly!" + RESET);
        System.exit(0);
    }

    /** Feature #10 / ISSUE-010 (Unified API Role Access Control): all tiers live in Feature10UnifiedRbacTest. */
    private static void runFeature10(int selectedTier) {
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        System.out.println(BOLD + CYAN + "    Zenith Feature #10: Unified Role-Based Access Control (roles / auth)       " + RESET);
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        long start = System.currentTimeMillis();
        List<TestHarness.TestResult> results = new ArrayList<>();
        String[] titles = {
                "Tier 1: DSL Parsing & AST Modeling",
                "Tier 2: Semantic Validation & Boundaries",
                "Tier 3: Spring Security Controller Code Generation",
                "Tier 4: Cross-Feature Integration with CRUD & Guards" };
        for (int t = 1; t <= 4; t++) {
            if (selectedTier == -1 || selectedTier == t) {
                runTier(t, titles[t - 1], Feature10UnifiedRbacTest.class, results, "^T" + t + "_.*");
            }
        }
        printSummary(results, System.currentTimeMillis() - start);
        boolean allPassed = results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println(RED + "\n[FAILURE] Some test cases did not pass. Check diagnostics above." + RESET);
            System.exit(1);
        }
        System.out.println(GREEN + "\n[SUCCESS] All " + results.size() + " test cases passed cleanly!" + RESET);
        System.exit(0);
    }

    /** Feature #13 (API Rate Limiting): all tiers live in Feature13RateLimitingTest. */
    private static void runFeature13(int selectedTier) {
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        System.out.println(BOLD + CYAN + "    Zenith Feature #13: API Rate Limiting (@rateLimit / Bucket4j)              " + RESET);
        System.out.println(BOLD + CYAN + "================================================================================" + RESET);
        long start = System.currentTimeMillis();
        List<TestHarness.TestResult> results = new ArrayList<>();
        String[] titles = {
                "Tier 1: DSL Parsing & AST Modeling",
                "Tier 2: Semantic Validation & Boundaries",
                "Tier 3: Spring Boot & Bucket4j Generator",
                "Tier 4: Spring Security Chain & Filter Integration" };
        for (int t = 1; t <= 4; t++) {
            if (selectedTier == -1 || selectedTier == t) {
                runTier(t, titles[t - 1], Feature13RateLimitingTest.class, results, "^T" + t + "_.*");
            }
        }
        printSummary(results, System.currentTimeMillis() - start);
        boolean allPassed = results.stream().allMatch(r -> r.passed);
        if (!allPassed) {
            System.err.println(RED + "\n[FAILURE] Some test cases did not pass. Check diagnostics above." + RESET);
            System.exit(1);
        }
        System.out.println(GREEN + "\n[SUCCESS] All " + results.size() + " test cases passed cleanly!" + RESET);
        System.exit(0);
    }

    private static void runTier(int tierNumber, String tierTitle, Class<?> testClass, List<TestHarness.TestResult> results) {
        runTier(tierNumber, tierTitle, testClass, results, "^T[1-4]_.*");
    }

    private static void runTier(int tierNumber, String tierTitle, Class<?> testClass, List<TestHarness.TestResult> results, String nameRegex) {
        System.out.println("\n" + BOLD + "--- Running " + tierTitle + " ---" + RESET);
        List<Method> methods = getSortedTestMethods(testClass, nameRegex);

        for (Method method : methods) {
            String testName = method.getName();
            long start = System.currentTimeMillis();
            boolean passed = false;
            String failureMessage = null;

            try {
                method.invoke(null);
                passed = true;
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                failureMessage = cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
            long duration = System.currentTimeMillis() - start;

            TestHarness.TestResult result = new TestHarness.TestResult(testName, tierNumber, passed, failureMessage, duration);
            results.add(result);

            if (passed) {
                System.out.println("  " + GREEN + "[PASS]" + RESET + " " + testName + " (" + duration + "ms)");
            } else {
                System.out.println("  " + RED + "[FAIL]" + RESET + " " + testName + " (" + duration + "ms)");
                System.out.println("         " + YELLOW + "-> " + failureMessage + RESET);
            }
        }
    }

    private static List<Method> getSortedTestMethods(Class<?> testClass, String nameRegex) {
        List<Method> methods = new ArrayList<>();
        for (Method m : testClass.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0 && m.getName().matches(nameRegex)) {
                methods.add(m);
            }
        }
        methods.sort(Comparator.comparing(Method::getName));
        return methods;
    }

    private static void printSummary(List<TestHarness.TestResult> results, long totalDuration) {
        System.out.println("\n" + BOLD + "================================================================================" + RESET);
        System.out.println(BOLD + "                            E2E Test Suite Summary                              " + RESET);
        System.out.println(BOLD + "================================================================================" + RESET);

        int total = results.size();
        long passed = results.stream().filter(r -> r.passed).count();
        long failed = total - passed;

        System.out.printf("  %-10s | %-12s | %-10s | %-10s | %-10s\n", "Tier", "Total", "Passed", "Failed", "Pass Rate");
        System.out.println("  ------------------------------------------------------------------");

        for (int t = 1; t <= 4; t++) {
            final int tierNum = t;
            long tierTotal = results.stream().filter(r -> r.tier == tierNum).count();
            if (tierTotal == 0) continue;
            long tierPassed = results.stream().filter(r -> r.tier == tierNum && r.passed).count();
            long tierFailed = tierTotal - tierPassed;
            double rate = (tierTotal > 0) ? (tierPassed * 100.0 / tierTotal) : 0.0;
            String color = tierFailed == 0 ? GREEN : RED;
            System.out.printf("  %-10s | %-12d | %-10d | %-10d | %s%6.1f%%%s\n",
                    "Tier " + tierNum, tierTotal, tierPassed, tierFailed, color, rate, RESET);
        }

        System.out.println("  ------------------------------------------------------------------");
        double totalRate = (total > 0) ? (passed * 100.0 / total) : 0.0;
        String totalColor = failed == 0 ? GREEN : RED;
        System.out.printf(BOLD + "  %-10s | %-12d | %-10d | %-10d | %s%6.1f%%%s\n" + RESET,
                "TOTAL", total, passed, failed, totalColor, totalRate, RESET);
        System.out.println(BOLD + "================================================================================" + RESET);
        System.out.printf("  Total Execution Duration: %d ms (%d tests executed)\n", totalDuration, total);

        if (failed > 0) {
            System.out.println("\n" + RED + BOLD + "Failed Test Details:" + RESET);
            for (TestHarness.TestResult r : results) {
                if (!r.passed) {
                    System.out.printf("  - [Tier %d] %s: %s\n", r.tier, r.testName, r.failureMessage);
                }
            }
        }
    }
}
