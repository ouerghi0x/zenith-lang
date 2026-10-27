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
        list.add(generateGlobalExceptionHandler(basePkg, pkgDir));

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

    private static JavaSourceFile generateGlobalExceptionHandler(String basePkg, String pkgDir) {
        String content = "package " + basePkg + ".exception;\n\n" +
                "import org.springframework.http.HttpStatus;\n" +
                "import org.springframework.http.ResponseEntity;\n" +
                "import org.springframework.validation.FieldError;\n" +
                "import org.springframework.web.bind.MethodArgumentNotValidException;\n" +
                "import org.springframework.web.bind.annotation.ExceptionHandler;\n" +
                "import org.springframework.web.bind.annotation.RestControllerAdvice;\n" +
                "import java.util.HashMap;\n" +
                "import java.util.Map;\n\n" +
                "@RestControllerAdvice\n" +
                "public class GlobalExceptionHandler {\n\n" +
                "    @ExceptionHandler(ResourceNotFoundException.class)\n" +
                "    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {\n" +
                "        ErrorResponse error = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), ex.getMessage());\n" +
                "        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);\n" +
                "    }\n\n" +
                "    @ExceptionHandler(MethodArgumentNotValidException.class)\n" +
                "    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {\n" +
                "        Map<String, String> errors = new HashMap<>();\n" +
                "        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {\n" +
                "            errors.put(fieldError.getField(), fieldError.getDefaultMessage());\n" +
                "        }\n" +
                "        ErrorResponse error = ErrorResponse.validation(HttpStatus.BAD_REQUEST.value(), \"Validation failed\", errors);\n" +
                "        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);\n" +
                "    }\n\n" +
                "    @ExceptionHandler(IllegalArgumentException.class)\n" +
                "    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {\n" +
                "        ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage());\n" +
                "        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);\n" +
                "    }\n\n" +
                "    @ExceptionHandler(Exception.class)\n" +
                "    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {\n" +
                "        ErrorResponse error = ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), \"An unexpected error occurred: \" + ex.getMessage());\n" +
                "        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);\n" +
                "    }\n" +
                "}\n";
        return new JavaSourceFile(pkgDir + "GlobalExceptionHandler.java", content);
    }
}
