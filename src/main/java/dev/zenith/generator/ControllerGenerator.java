package dev.zenith.generator;

import dev.zenith.ast.*;

public class ControllerGenerator {

    public static JavaSourceFile generate(ModelDef model, ZenithFile file) {
        String basePkg = file.getConfig().getPackage();
        String className = model.getName();
        String controllerName = className + "Controller";
        String serviceName = className + "Service";
        String basePath = file.getConfig().getBasePath() + "/"
                + TypeHelper.toSnakeCase(TypeHelper.pluralize(className));

        FieldDef idField = model.getIdField();
        String idType = idField != null ? TypeHelper.toSimpleJavaType(idField.getType()) : "Long";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".controller;\n\n");

        sb.append("import ").append(basePkg).append(".dto.*;\n");
        sb.append("import ").append(basePkg).append(".service.").append(serviceName).append(";\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import io.swagger.v3.oas.annotations.Operation;\n");
        sb.append("import io.swagger.v3.oas.annotations.tags.Tag;\n");
        sb.append("import jakarta.validation.Valid;\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.http.ResponseEntity;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n");
        sb.append("import java.net.URI;\n");
        sb.append("import java.util.List;\n\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"").append(basePath).append("\")\n");
        sb.append("@CrossOrigin\n");
        sb.append("@Tag(name = \"").append(className).append("\", description = \"Operations for managing ")
                .append(TypeHelper.pluralize(className)).append("\")\n");
        sb.append("public class ").append(controllerName).append(" {\n\n");

        sb.append("    private final ").append(serviceName).append(" service;\n\n");

        sb.append("    public ").append(controllerName).append("(").append(serviceName).append(" service) {\n");
        sb.append("        this.service = service;\n");
        sb.append("    }\n\n");

        // GET / (list)
        StringBuilder ctrlMethodParams = new StringBuilder();
        ctrlMethodParams.append("        @RequestParam(defaultValue = \"0\") int page,\n");
        ctrlMethodParams.append("        @RequestParam(defaultValue = \"20\") int size,\n");
        ctrlMethodParams.append("        @RequestParam(defaultValue = \"id\") String sort,\n");
        ctrlMethodParams.append("        @RequestParam(defaultValue = \"asc\") String direction,\n");
        ctrlMethodParams.append("        @RequestParam(required = false) String search");

        StringBuilder serviceCallArgs = new StringBuilder("page, size, sort, direction, search");

        CrudConfig crud = model.getCrudConfig();
        if (crud != null) {
            for (String ff : crud.getFilterFields()) {
                FieldDef fDef = model.findField(ff);
                if (fDef != null) {
                    String fType = TypeHelper.toSimpleJavaType(fDef.getType());
                    ctrlMethodParams.append(",\n        @RequestParam(required = false) ").append(fType).append(" ").append(ff);
                    serviceCallArgs.append(", ").append(ff);
                }
            }
        }
        ctrlMethodParams.append("\n    ");

        sb.append("    @GetMapping\n");
        sb.append("    @Operation(summary = \"List all ").append(TypeHelper.pluralize(className))
                .append(" with pagination, sorting, and search\")\n");
        sb.append("    public ResponseEntity<PageResponse<").append(className).append("Response>> getAll(\n");
        sb.append(ctrlMethodParams).append(") {\n");
        sb.append("        return ResponseEntity.ok(service.getAll(").append(serviceCallArgs).append("));\n");
        sb.append("    }\n\n");

        // GET /{id}
        sb.append("    @GetMapping(\"/{id}\")\n");
        sb.append("    @Operation(summary = \"Get a ").append(className).append(" by ID\")\n");
        sb.append("    public ResponseEntity<").append(className).append("Response> getById(@PathVariable ")
                .append(idType).append(" id) {\n");
        sb.append("        return ResponseEntity.ok(service.getById(id));\n");
        sb.append("    }\n\n");

        // POST /
        sb.append("    @PostMapping\n");
        sb.append("    @Operation(summary = \"Create a new ").append(className).append("\")\n");
        sb.append("    public ResponseEntity<").append(className).append("Response> create(@Valid @RequestBody Create")
                .append(className).append("Request request) {\n");
        sb.append("        ").append(className).append("Response created = service.create(request);\n");
        sb.append("        return ResponseEntity.status(HttpStatus.CREATED).body(created);\n");
        sb.append("    }\n\n");

        // PUT /{id}
        sb.append("    @PutMapping(\"/{id}\")\n");
        sb.append("    @Operation(summary = \"Update an existing ").append(className).append(" by ID\")\n");
        sb.append("    public ResponseEntity<").append(className).append("Response> update(\n");
        sb.append("        @PathVariable ").append(idType).append(" id,\n");
        sb.append("        @Valid @RequestBody Update").append(className).append("Request request\n");
        sb.append("    ) {\n");
        sb.append("        return ResponseEntity.ok(service.update(id, request));\n");
        sb.append("    }\n\n");

        // DELETE /{id}
        sb.append("    @DeleteMapping(\"/{id}\")\n");
        sb.append("    @Operation(summary = \"Delete a ").append(className).append(" by ID\")\n");
        sb.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
        sb.append("        service.delete(id);\n");
        sb.append("        return ResponseEntity.noContent().build();\n");
        sb.append("    }\n\n");

        // Custom endpoints
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String path = custom.getPath().startsWith("/") ? custom.getPath() : "/" + custom.getPath();
            String springMethod = "@" + TypeHelper.capitalize(custom.getMethod().toLowerCase()) + "Mapping(\"" + path
                    + "\")";
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            String summary = custom.getDescription().isEmpty() ? "Custom action: " + methodName
                    : custom.getDescription();

            sb.append("    ").append(springMethod).append("\n");
            sb.append("    @Operation(summary = \"").append(summary.replace("\"", "\\\"")).append("\")\n");
            if ("void".equals(ret)) {
                sb.append("    public ResponseEntity<Void> ").append(methodName).append("(@PathVariable ")
                        .append(idType).append(" id) {\n");
                sb.append("        service.").append(methodName).append("(id);\n");
                sb.append("        return ResponseEntity.ok().build();\n");
                sb.append("    }\n\n");
            } else {
                sb.append("    public ResponseEntity<").append(ret).append("> ").append(methodName)
                        .append("(@PathVariable ").append(idType).append(" id) {\n");
                sb.append("        return ResponseEntity.ok(service.").append(methodName).append("(id));\n");
                sb.append("    }\n\n");
            }
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/controller/" + controllerName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }

    private static String toCamelCase(String s) {
        String[] parts = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(TypeHelper.capitalize(part));
        }
        return sb.toString();
    }
}
