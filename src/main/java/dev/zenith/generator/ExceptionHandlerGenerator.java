package dev.zenith.generator;

import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

public class ExceptionHandlerGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        String basePkg = file.getConfig().getPackage();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/exception/";

        list.add(generateResourceNotFoundException(basePkg, pkgDir));
        list.add(generateErrorResponse(basePkg, pkgDir));
        list.add(generateGlobalExceptionHandler(basePkg, pkgDir, file.hasAuth()));

        return list;
    }

    private static JavaSourceFile generateResourceNotFoundException(String basePkg, String pkgDir) {
        String content = "package " + basePkg + ".exception;\n\n" +
                "import org.springframework.http.HttpStatus;\n" +
                "import org.springframework.web.bind.annotation.ResponseStatus;\n\n" +
                "@ResponseStatus(HttpStatus.NOT_FOUND)\n" +
                "public class ResourceNotFoundException extends RuntimeException {\n" +
                "    public ResourceNotFoundException(String message) {\n" +
                "        super(message);\n" +
                "    }\n" +
                "}\n";
        return new JavaSourceFile(pkgDir + "ResourceNotFoundException.java", content);
    }

    private static JavaSourceFile generateErrorResponse(String basePkg, String pkgDir) {
        String content = "package " + basePkg + ".exception;\n\n" +
                "import java.time.LocalDateTime;\n" +
                "import java.util.Map;\n\n" +
                "public record ErrorResponse(\n" +
                "    int status,\n" +
                "    String message,\n" +
                "    LocalDateTime timestamp,\n" +
                "    Map<String, String> fieldErrors\n" +
                ") {\n" +
                "    public static ErrorResponse of(int status, String message) {\n" +
                "        return new ErrorResponse(status, message, LocalDateTime.now(), null);\n" +
                "    }\n\n" +
                "    public static ErrorResponse validation(int status, String message, Map<String, String> fieldErrors) {\n" +
                "        return new ErrorResponse(status, message, LocalDateTime.now(), fieldErrors);\n" +
                "    }\n" +
                "}\n";
        return new JavaSourceFile(pkgDir + "ErrorResponse.java", content);
    }

    private static JavaSourceFile generateGlobalExceptionHandler(String basePkg, String pkgDir, boolean hasAuth) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".exception;\n\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.validation.FieldError;\n");
        sb.append("import org.springframework.web.bind.MethodArgumentNotValidException;\n");
        sb.append("import org.springframework.web.bind.annotation.ExceptionHandler;\n");
        sb.append("import org.springframework.web.bind.annotation.RestControllerAdvice;\n");
        if (hasAuth) {
            sb.append("import org.springframework.security.access.AccessDeniedException;\n");
        }
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n\n");
        sb.append("@RestControllerAdvice\n");
        sb.append("public class GlobalExceptionHandler {\n\n");
        sb.append("    @ExceptionHandler(ResourceNotFoundException.class)\n");
        sb.append("    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {\n");
        sb.append("        ErrorResponse error = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());\n");
        sb.append("        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);\n");
        sb.append("    }\n\n");
        sb.append("    @ExceptionHandler(MethodArgumentNotValidException.class)\n");
        sb.append("    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {\n");
        sb.append("        Map<String, String> errors = new HashMap<>();\n");
        sb.append("        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {\n");
        sb.append("            errors.put(fieldError.getField(), fieldError.getDefaultMessage());\n");
        sb.append("        }\n");
        sb.append("        ErrorResponse error = ErrorResponse.validation(HttpStatus.BAD_REQUEST.value(), \"Validation failed\", errors);\n");
        sb.append("        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);\n");
        sb.append("    }\n\n");
        sb.append("    @ExceptionHandler(IllegalArgumentException.class)\n");
        sb.append("    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {\n");
        sb.append("        ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());\n");
        sb.append("        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);\n");
        sb.append("    }\n\n");
        if (hasAuth) {
            sb.append("    @ExceptionHandler(AccessDeniedException.class)\n");
            sb.append("    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {\n");
            sb.append("        ErrorResponse error = ErrorResponse.of(HttpStatus.FORBIDDEN.value(), \"Access Denied: Insufficient permissions\");\n");
            sb.append("        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);\n");
            sb.append("    }\n\n");
        }
        sb.append("    @ExceptionHandler(Exception.class)\n");
        sb.append("    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {\n");
        sb.append("        ErrorResponse error = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), \"An unexpected error occurred: \" + ex.getMessage());\n");
        sb.append("        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return new JavaSourceFile(pkgDir + "GlobalExceptionHandler.java", sb.toString());
    }
}
