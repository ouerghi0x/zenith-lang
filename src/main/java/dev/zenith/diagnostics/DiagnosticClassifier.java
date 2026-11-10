package dev.zenith.diagnostics;

import dev.zenith.generator.trace.GenerationMapping;

/**
 * Classifies javac and Maven errors into:
 * - GENERATOR_DEFECT
 * - SPECIFICATION_DEFECT
 * - ENVIRONMENT_DEFECT
 * - EXTERNAL_DEPENDENCY
 */
public class DiagnosticClassifier {

    public static CompilerDiagnostic.ErrorCategory classify(String errorMessage, GenerationMapping mapping) {
        if (errorMessage == null) {
            return CompilerDiagnostic.ErrorCategory.SPECIFICATION_DEFECT;
        }

        String lower = errorMessage.toLowerCase();

        // 1. Environment defects
        if (lower.contains("java heap space")
                || lower.contains("outofmemoryerror")
                || lower.contains("invalid target release")
                || lower.contains("unrecognized option")
                || lower.contains("source release") && lower.contains("requires target release")) {
            return CompilerDiagnostic.ErrorCategory.ENVIRONMENT_DEFECT;
        }

        // 2. External dependencies
        if (lower.contains("could not resolve dependencies")
                || lower.contains("transfer failed")
                || lower.contains("connection refused")
                || lower.contains("artifactnotfoundexception")) {
            return CompilerDiagnostic.ErrorCategory.EXTERNAL_DEPENDENCY;
        }

        // 3. Generator Defect (compiler emitted missing imports, bad syntax, annotation targets)
        if (lower.contains("package org.springframework") && lower.contains("does not exist")
                || lower.contains("package jakarta.") && lower.contains("does not exist")
                || lower.contains("annotation type not applicable")
                || lower.contains("not applicable to type")
                || lower.contains("illegal start of expression")
                || lower.contains("';' expected")
                || lower.contains("')' expected")
                || lower.contains("class, interface, or enum expected")
                || lower.contains("cannot find symbol") && (lower.contains("class tag") || lower.contains("class operation"))) {
            return CompilerDiagnostic.ErrorCategory.GENERATOR_DEFECT;
        }

        // 4. Specification Defect (user type mismatch, bad operand, missing property in expression)
        if (lower.contains("incompatible types")
                || lower.contains("bad operand types")
                || lower.contains("cannot be converted to")
                || lower.contains("cannot find symbol")
                || lower.contains("operator") && lower.contains("cannot be applied")) {
            return CompilerDiagnostic.ErrorCategory.SPECIFICATION_DEFECT;
        }

        return CompilerDiagnostic.ErrorCategory.GENERATOR_DEFECT;
    }
}

