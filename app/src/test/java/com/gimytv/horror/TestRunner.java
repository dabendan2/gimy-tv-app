package com.gimytv.horror;

public class TestRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   Gimy TV App - Automated Unit Test Suite       ");
        System.out.println("=================================================");

        int passed = 0;
        int failed = 0;

        // Run GimyParser Tests
        try {
            GimyParserTest.runTests();
            passed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] GimyParser tests failed!");
            t.printStackTrace();
            failed++;
        }

        System.out.println("=================================================");
        System.out.println("TEST RUN SUMMARY:");
        System.out.println("  PASSED MODULES: " + passed);
        System.out.println("  FAILED MODULES: " + failed);
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.out.println("SUCCESS: All unit tests completed without errors! No regressions.");
            System.exit(0);
        }
    }
}
