package dev.zenith.generator;

import dev.zenith.ast.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        if (file.hasAuth()) {
            sb.append("import org.springframework.security.access.prepost.PreAuthorize;\n");
        }
        sb.append("import java.net.URI;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n\n");
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

        CrudConfig crud = model.getCrudConfig();
        boolean hasAuth = file.hasAuth();

        if (crud != null) {
            // GET / (list)
            StringBuilder ctrlMethodParams = new StringBuilder();
            ctrlMethodParams.append("        @RequestParam(defaultValue = \"0\") int page,\n");
            ctrlMethodParams.append("        @RequestParam(defaultValue = \"20\") int size,\n");
            ctrlMethodParams.append("        @RequestParam(defaultValue = \"id\") String sort,\n");
            ctrlMethodParams.append("        @RequestParam(defaultValue = \"asc\") String direction,\n");
            ctrlMethodParams.append("        @RequestParam(required = false) String search");

            StringBuilder serviceCallArgs = new StringBuilder("page, size, sort, direction, search");

            for (String ff : crud.getFilterFields()) {
                FieldDef fDef = model.findField(ff);
                String fType = null;
                if (fDef != null) {
                    fType = TypeHelper.toSimpleJavaType(fDef.getType());
                } else {
                    for (RelationDef r : model.getRelations()) {
                        if (r.getFieldName().equalsIgnoreCase(ff) || (r.getFieldName() + "Id").equalsIgnoreCase(ff)) {
                            fType = DtoGenerator.resolveTargetIdType(r.getTargetModel(), file);
                            break;
                        }
                    }
                }
                if (fType != null) {
                    ctrlMethodParams.append(",\n        @RequestParam(required = false) ").append(fType).append(" ")
                            .append(ff);
                    serviceCallArgs.append(", ").append(ff);
                }
            }
            ctrlMethodParams.append("\n    ");

            sb.append("    @GetMapping\n");
            sb.append("    @Operation(summary = \"List all ").append(TypeHelper.pluralize(className))
                    .append(" with pagination, sorting, and search\")\n");
            if (hasAuth) {
                sb.append("    ").append(getCrudPreAuthorize(crud, "getAll", true));
            }
            sb.append("    public ResponseEntity<PageResponse<").append(className).append("Response>> getAll(\n");
            sb.append(ctrlMethodParams).append(") {\n");
            sb.append("        return ResponseEntity.ok(service.getAll(").append(serviceCallArgs).append("));\n");
            sb.append("    }\n\n");

            // GET /{id}
            sb.append("    @GetMapping(\"/{id}\")\n");
            sb.append("    @Operation(summary = \"Get a ").append(className).append(" by ID\")\n");
            if (hasAuth) {
                sb.append("    ").append(getCrudPreAuthorize(crud, "getById", true));
            }
            sb.append("    public ResponseEntity<").append(className).append("Response> getById(@PathVariable ")
                    .append(idType).append(" id) {\n");
            sb.append("        return ResponseEntity.ok(service.getById(id));\n");
            sb.append("    }\n\n");

            // POST /
            sb.append("    @PostMapping\n");
            sb.append("    @Operation(summary = \"Create a new ").append(className).append("\")\n");
            if (hasAuth) {
                sb.append("    ").append(getCrudPreAuthorize(crud, "create", false));
            }
            sb.append("    public ResponseEntity<").append(className)
                    .append("Response> create(@Valid @RequestBody Create")
                    .append(className).append("Request request) {\n");
            sb.append("        ").append(className).append("Response created = service.create(request);\n");
            sb.append("        return ResponseEntity.status(HttpStatus.CREATED).body(created);\n");
            sb.append("    }\n\n");

            // PUT /{id}
            sb.append("    @PutMapping(\"/{id}\")\n");
            sb.append("    @Operation(summary = \"Update an existing ").append(className).append(" by ID\")\n");
            if (hasAuth) {
                sb.append("    ").append(getCrudPreAuthorize(crud, "update", false));
            }
            sb.append("    public ResponseEntity<").append(className).append("Response> update(\n");
            sb.append("        @PathVariable ").append(idType).append(" id,\n");
            sb.append("        @Valid @RequestBody Update").append(className).append("Request request\n");
            sb.append("    ) {\n");
            sb.append("        return ResponseEntity.ok(service.update(id, request));\n");
            sb.append("    }\n\n");

            // DELETE /{id}
            sb.append("    @DeleteMapping(\"/{id}\")\n");
            sb.append("    @Operation(summary = \"Delete a ").append(className).append(" by ID\")\n");
            if (hasAuth) {
                sb.append("    ").append(getCrudPreAuthorize(crud, "delete", false));
            }
            sb.append("    public ResponseEntity<Void> delete(@PathVariable ").append(idType).append(" id) {\n");
            sb.append("        service.delete(id);\n");
            sb.append("        return ResponseEntity.noContent().build();\n");
            sb.append("    }\n\n");
        }

        // Custom endpoints
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String path = custom.getPath().startsWith("/") ? custom.getPath() : "/" + custom.getPath();
            String springPath = TypeHelper.sanitizePath(path);
            String springMethod = "@" + TypeHelper.capitalize(custom.getMethod().toLowerCase()) + "Mapping(\""
                    + springPath
                    + "\")";
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            String summary = custom.getDescription().isEmpty() ? "Custom action: " + methodName
                    : custom.getDescription();

            sb.append("    ").append(springMethod).append("\n");
            sb.append("    @Operation(summary = \"").append(summary.replace("\"", "\\\"")).append("\")\n");

            if (hasAuth) {
                List<String> guardRoles = new ArrayList<>();
                for (GuardDef g : custom.getGuards()) {
                    for (String r : g.extractRoles()) {
                        if (!guardRoles.contains(r)) {
                            guardRoles.add(r);
                        }
                    }
                }

                if (!guardRoles.isEmpty()) {
                    if (guardRoles.size() == 1) {
                        sb.append("    @PreAuthorize(\"hasRole('").append(guardRoles.get(0)).append("')\")\n");
                    } else {
                        String joined = guardRoles.stream().map(r -> "'" + r + "'").collect(Collectors.joining(", "));
                        sb.append("    @PreAuthorize(\"hasAnyRole(").append(joined).append(")\")\n");
                    }
                } else if (Boolean.FALSE.equals(custom.getRequiresAuth())) {
                    sb.append("    @PreAuthorize(\"permitAll()\")\n");
                } else if (Boolean.TRUE.equals(custom.getRequiresAuth()) || custom.isUserScoped(methodName)) {
                    sb.append("    @PreAuthorize(\"isAuthenticated()\")\n");
                } else {
                    if ("GET".equalsIgnoreCase(custom.getMethod())) {
                        sb.append("    @PreAuthorize(\"permitAll()\")\n");
                    } else {
                        sb.append("    @PreAuthorize(\"isAuthenticated()\")\n");
                    }
                }
            }

            List<TypeHelper.PathVariable> vars = TypeHelper.extractPathVariables(custom.getPath(), model, idType,
                    custom);
            List<String> paramParts = new ArrayList<>();
            List<String> argParts = new ArrayList<>();
            if (custom.hasBody()) {
                paramParts.add("@Valid @RequestBody " + custom.getBodyType() + " body");
                argParts.add("body");
            }
            for (TypeHelper.PathVariable v : vars) {
                paramParts.add("@PathVariable " + v.getType() + " " + v.getName());
                argParts.add(v.getName());
            }
            String ctrlParams = String.join(", ", paramParts);
            String callArgs = String.join(", ", argParts);

            if ("void".equals(ret)) {
                sb.append("    public ResponseEntity<Void> ").append(methodName).append("(")
                        .append(ctrlParams).append(") {\n");
                sb.append("        service.").append(methodName).append("(").append(callArgs).append(");\n");
                sb.append("        return ResponseEntity.ok().build();\n");
                sb.append("    }\n\n");
            } else {
                sb.append("    public ResponseEntity<").append(ret).append("> ").append(methodName)
                        .append("(").append(ctrlParams).append(") {\n");
                sb.append("        return ResponseEntity.ok(service.").append(methodName).append("(")
                        .append(callArgs).append("));\n");
                sb.append("    }\n\n");
            }
        }

        if (model.hasStorageEndpoints()) {
            for (FieldDef field : model.getFields()) {
                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
                    String fieldName = field.getName();
                    String fieldCap = TypeHelper.capitalize(fieldName);
                    boolean isImage = field.hasAnnotation("image");
                    String desc = isImage ? "image" : "file";

                    // POST /{id}/fieldName
                    sb.append("    @PostMapping(value = \"/{id}/").append(fieldName)
                            .append("\", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)\n");
                    sb.append("    @Operation(summary = \"Upload ").append(fieldName).append(" ").append(desc).append("\")\n");
                    if (hasAuth) {
                        sb.append("    @PreAuthorize(\"isAuthenticated()\")\n");
                    }
                    sb.append("    public ResponseEntity<").append(className).append("Response> upload").append(fieldCap).append("(\n");
                    sb.append("            @PathVariable ").append(idType).append(" id,\n");
                    sb.append("            @RequestParam(\"file\") org.springframework.web.multipart.MultipartFile file\n");
                    sb.append("    ) {\n");
                    sb.append("        return ResponseEntity.ok(service.upload").append(fieldCap).append("(id, file));\n");
                    sb.append("    }\n\n");

                    // GET /{id}/fieldName
                    sb.append("    @GetMapping(\"/{id}/").append(fieldName).append("\")\n");
                    sb.append("    @Operation(summary = \"Download ").append(fieldName).append(" ").append(desc).append("\")\n");
                    sb.append("    public ResponseEntity<org.springframework.core.io.Resource> download").append(fieldCap).append("(@PathVariable ").append(idType).append(" id) {\n");
                    sb.append("        ").append(basePkg).append(".storage.FileResource resource = service.download").append(fieldCap).append("(id);\n");
                    sb.append("        return ResponseEntity.ok()\n");
                    sb.append("                .contentType(org.springframework.http.MediaType.parseMediaType(resource.contentType()))\n");
                    sb.append("                .contentLength(resource.contentLength())\n");
                    sb.append("                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, \"inline; filename=\\\"\" + resource.filename() + \"\\\"\")\n");
                    sb.append("                .body(new org.springframework.core.io.InputStreamResource(resource.inputStream()));\n");
                    sb.append("    }\n\n");

                    // DELETE /{id}/fieldName
                    sb.append("    @DeleteMapping(\"/{id}/").append(fieldName).append("\")\n");
                    sb.append("    @Operation(summary = \"Delete ").append(fieldName).append(" ").append(desc).append("\")\n");
                    if (hasAuth) {
                        sb.append("    @PreAuthorize(\"isAuthenticated()\")\n");
                    }
                    sb.append("    public ResponseEntity<Void> delete").append(fieldCap).append("(@PathVariable ").append(idType).append(" id) {\n");
                    sb.append("        service.delete").append(fieldCap).append("(id);\n");
                    sb.append("        return ResponseEntity.noContent().build();\n");
                    sb.append("    }\n\n");
                }
            }
        }

        sb.append("}\n");

        String path = "src/main/java/" + basePkg.replace('.', '/') + "/controller/" + controllerName + ".java";
        return new JavaSourceFile(path, sb.toString());
    }

    private static String getCrudPreAuthorize(CrudConfig crud, String operation, boolean defaultIsPublic) {
        if (crud != null) {
            if (crud.isOperationPublic(operation)) {
                return "@PreAuthorize(\"permitAll()\")\n";
            }
            List<String> roles = crud.getRolesForOperation(operation);
            if (roles != null && !roles.isEmpty()) {
                if (roles.size() == 1) {
                    String r = roles.get(0);
                    if ("public".equalsIgnoreCase(r))
                        return "@PreAuthorize(\"permitAll()\")\n";
                    if ("authenticated".equalsIgnoreCase(r) || "auth".equalsIgnoreCase(r))
                        return "@PreAuthorize(\"isAuthenticated()\")\n";
                    return "@PreAuthorize(\"hasRole('" + r + "')\")\n";
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < roles.size(); i++) {
                        if (i > 0)
                            sb.append(", ");
                        sb.append("'").append(roles.get(i)).append("'");
                    }
                    return "@PreAuthorize(\"hasAnyRole(" + sb.toString() + ")\")\n";
                }
            }
            if (Boolean.TRUE.equals(crud.getRequiresAuth())) {
                return "@PreAuthorize(\"isAuthenticated()\")\n";
            }
            if (Boolean.FALSE.equals(crud.getRequiresAuth())) {
                return "@PreAuthorize(\"permitAll()\")\n";
            }
        }
        return defaultIsPublic ? "@PreAuthorize(\"permitAll()\")\n" : "@PreAuthorize(\"isAuthenticated()\")\n";
    }
}
