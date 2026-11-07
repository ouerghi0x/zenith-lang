package dev.zenith.generator;

import dev.zenith.ast.*;

import java.util.ArrayList;
import java.util.List;

public class ServiceGenerator {

    public static List<JavaSourceFile> generate(ModelDef model, ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        String basePkg = file.getConfig().getPackage();
        String className = model.getName();
        String pkgDir = "src/main/java/" + basePkg.replace('.', '/') + "/service/";

        list.add(generateInterface(model, file, pkgDir, basePkg, className));
        list.add(generateImplementation(model, file, pkgDir, basePkg, className));

        return list;
    }

    private static JavaSourceFile generateInterface(ModelDef model, ZenithFile file, String pkgDir, String basePkg,
            String className) {
        String serviceName = className + "Service";
        FieldDef idField = model.getIdField();
        String idType = idField != null ? TypeHelper.toSimpleJavaType(idField.getType()) : "Long";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".service;\n\n");

        sb.append("import ").append(basePkg).append(".dto.*;\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import java.util.List;\n");
        sb.append("import java.util.UUID;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n\n");

        StringBuilder getAllParams = new StringBuilder(
                "int page, int size, String sort, String direction, String search");
        CrudConfig crud = model.getCrudConfig();
        if (crud != null) {
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
                    getAllParams.append(", ").append(fType).append(" ").append(ff);
                }
            }
        }

        sb.append("public interface ").append(serviceName).append(" {\n");
        if (crud != null) {
            sb.append("    PageResponse<").append(className)
                    .append("Response> getAll(").append(getAllParams).append(");\n");
            sb.append("    ").append(className).append("Response getById(").append(idType).append(" id);\n");
            sb.append("    ").append(className).append("Response create(Create").append(className)
                    .append("Request request);\n");
            sb.append("    ").append(className).append("Response update(").append(idType).append(" id, Update")
                    .append(className).append("Request request);\n");
            sb.append("    void delete(").append(idType).append(" id);\n");
        }

        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String bodyType = custom.hasBody() ? custom.getBodyType() : null;
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            List<TypeHelper.PathVariable> vars = TypeHelper.extractPathVariables(custom.getPath(), model, idType,
                    custom);
            List<String> paramParts = new ArrayList<>();
            if (bodyType != null && !bodyType.isEmpty()) {
                paramParts.add(bodyType + " body");
            }
            for (TypeHelper.PathVariable v : vars) {
                paramParts.add(v.getType() + " " + v.getName());
            }
            sb.append("    ").append(ret).append(" ").append(methodName).append("(")
                    .append(String.join(", ", paramParts)).append(");\n");
        }

        if (model.hasStorageEndpoints()) {
            for (FieldDef field : model.getFields()) {
                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
                    String fieldCap = TypeHelper.capitalize(field.getName());
                    sb.append("    ").append(className).append("Response upload").append(fieldCap)
                            .append("(").append(idType).append(" id, org.springframework.web.multipart.MultipartFile file);\n");
                    sb.append("    ").append(basePkg).append(".storage.FileResource download").append(fieldCap)
                            .append("(").append(idType).append(" id);\n");
                    sb.append("    void delete").append(fieldCap).append("(").append(idType).append(" id);\n");
                }
            }
        }

        sb.append("}\n");

        return new JavaSourceFile(pkgDir + serviceName + ".java", sb.toString());
    }

    private static JavaSourceFile generateImplementation(ModelDef model, ZenithFile file, String pkgDir, String basePkg,
            String className) {
        String serviceName = className + "Service";
        String implName = className + "ServiceImpl";
        String repoName = className + "Repository";
        String mapperName = className + "Mapper";
        FieldDef idField = model.getIdField();
        String idType = idField != null ? TypeHelper.toSimpleJavaType(idField.getType()) : "Long";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".service.impl;\n\n");

        sb.append("import ").append(basePkg).append(".service.").append(serviceName).append(";\n");
        sb.append("import ").append(basePkg).append(".entity.").append(className).append(";\n");
        sb.append("import ").append(basePkg).append(".dto.*;\n");
        sb.append("import ").append(basePkg).append(".mapper.").append(mapperName).append(";\n");
        sb.append("import ").append(basePkg).append(".repository.").append(repoName).append(";\n");
        if (!file.getEnums().isEmpty()) {
            sb.append("import ").append(basePkg).append(".model.*;\n");
        }
        sb.append("import java.util.UUID;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDateTime;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import ").append(basePkg).append(".exception.ResourceNotFoundException;\n");

        // Relation repository imports
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("import ").append(basePkg).append(".repository.").append(rel.getTargetModel())
                        .append("Repository;\n");
                sb.append("import ").append(basePkg).append(".entity.").append(rel.getTargetModel()).append(";\n");
            }
        }

        // Extra repositories referenced in action statements (e.g. create AuditLog, update Comment)
        java.util.Set<String> extraRepos = new java.util.LinkedHashSet<>();
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            for (String stmt : custom.getActionStatements()) {
                String t = stmt.trim();
                if (t.startsWith("create ")) {
                    String rest = t.substring(7).trim();
                    int space = rest.indexOf(' ');
                    int brace = rest.indexOf('{');
                    int end = space > 0 ? (brace > 0 ? Math.min(space, brace) : space) : (brace > 0 ? brace : rest.length());
                    String mName = rest.substring(0, end).trim();
                    if (!mName.isEmpty() && !mName.equalsIgnoreCase(className)) {
                        extraRepos.add(mName);
                    }
                } else if (t.startsWith("update ")) {
                    String rest = t.substring(7).trim();
                    int space = rest.indexOf(' ');
                    int brace = rest.indexOf('{');
                    int end = space > 0 ? (brace > 0 ? Math.min(space, brace) : space) : (brace > 0 ? brace : rest.length());
                    String mName = rest.substring(0, end).trim();
                    if (!mName.isEmpty() && !mName.equalsIgnoreCase(className)) {
                        extraRepos.add(mName);
                    }
                }
            }
        }
        for (RelationDef rel : model.getRelations()) {
            extraRepos.remove(rel.getTargetModel());
        }
        for (String extra : extraRepos) {
            sb.append("import ").append(basePkg).append(".repository.").append(extra).append("Repository;\n");
            sb.append("import ").append(basePkg).append(".entity.").append(extra).append(";\n");
        }

        boolean hasStorageFields = model.hasStorageEndpoints();
        if (hasStorageFields) {
            sb.append("import ").append(basePkg).append(".storage.StorageService;\n");
            sb.append("import ").append(basePkg).append(".storage.FileResource;\n");
            sb.append("import ").append(basePkg).append(".storage.FileValidationRules;\n");
            sb.append("import ").append(basePkg).append(".entity.FileMetadata;\n");
            sb.append("import org.springframework.web.multipart.MultipartFile;\n");
        }

        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import org.springframework.data.domain.Page;\n");
        sb.append("import org.springframework.data.domain.PageRequest;\n");
        sb.append("import org.springframework.data.domain.Pageable;\n");
        sb.append("import org.springframework.data.domain.Sort;\n");
        sb.append("import org.springframework.data.jpa.domain.Specification;\n");
        sb.append("import jakarta.persistence.criteria.Predicate;\n");
        sb.append("import java.util.ArrayList;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@Service\n");
        sb.append("@Transactional\n");
        sb.append("public class ").append(implName).append(" implements ").append(serviceName).append(" {\n\n");

        sb.append("    private final ").append(repoName).append(" repository;\n");
        sb.append("    private final ").append(mapperName).append(" mapper;\n");

        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("    private final ").append(rel.getTargetModel()).append("Repository ")
                        .append(rel.getTargetModel().toLowerCase()).append("Repository;\n");
            }
        }
        for (String extra : extraRepos) {
            String rVar = Character.toLowerCase(extra.charAt(0)) + extra.substring(1) + "Repository";
            sb.append("    private final ").append(extra).append("Repository ").append(rVar).append(";\n");
        }
        if (hasStorageFields) {
            sb.append("    private final StorageService storageService;\n");
        }
        sb.append("\n");

        // Constructor injection
        sb.append("    public ").append(implName).append("(\n");
        sb.append("        ").append(repoName).append(" repository,\n");
        sb.append("        ").append(mapperName).append(" mapper");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append(",\n        ").append(rel.getTargetModel()).append("Repository ")
                        .append(rel.getTargetModel().toLowerCase()).append("Repository");
            }
        }
        for (String extra : extraRepos) {
            String rVar = Character.toLowerCase(extra.charAt(0)) + extra.substring(1) + "Repository";
            sb.append(",\n        ").append(extra).append("Repository ").append(rVar);
        }
        if (hasStorageFields) {
            sb.append(",\n        StorageService storageService");
        }
        sb.append("\n    ) {\n");
        sb.append("        this.repository = repository;\n");
        sb.append("        this.mapper = mapper;\n");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("        this.").append(rel.getTargetModel().toLowerCase()).append("Repository = ")
                        .append(rel.getTargetModel().toLowerCase()).append("Repository;\n");
            }
        }
        for (String extra : extraRepos) {
            String rVar = Character.toLowerCase(extra.charAt(0)) + extra.substring(1) + "Repository";
            sb.append("        this.").append(rVar).append(" = ").append(rVar).append(";\n");
        }
        if (hasStorageFields) {
            sb.append("        this.storageService = storageService;\n");
        }
        sb.append("    }\n\n");

        // getAll
        StringBuilder implGetAllParams = new StringBuilder(
                "int page, int size, String sort, String direction, String search");
        CrudConfig crud = model.getCrudConfig();
        if (crud != null) {
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
                    implGetAllParams.append(", ").append(fType).append(" ").append(ff);
                }
            }
        }

        if (crud != null) {
            sb.append("    @Override\n");
            sb.append("    @Transactional(readOnly = true)\n");
            sb.append("    public PageResponse<").append(className)
                    .append("Response> getAll(").append(implGetAllParams).append(") {\n");
            sb.append(
                    "        Sort sortObj = \"desc\".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();\n");
            sb.append("        Pageable pageable = PageRequest.of(page, size, sortObj);\n");
            sb.append("        Specification<").append(className).append("> spec = (root, query, cb) -> {\n");
            sb.append("            List<Predicate> predicates = new ArrayList<>();\n");

            if (crud != null && !crud.getSearchFields().isEmpty()) {
                sb.append("            if (search != null && !search.trim().isEmpty()) {\n");
                sb.append("                String pattern = \"%\" + search.trim().toLowerCase() + \"%\";\n");
                sb.append("                List<Predicate> searchPreds = new ArrayList<>();\n");
                for (String sf : crud.getSearchFields()) {
                    sb.append("                searchPreds.add(cb.like(cb.lower(root.get(\"").append(sf)
                            .append("\")), pattern));\n");
                }
                sb.append("                predicates.add(cb.or(searchPreds.toArray(new Predicate[0])));\n");
                sb.append("            }\n");
            }

            if (crud != null && !crud.getFilterFields().isEmpty()) {
                for (String ff : crud.getFilterFields()) {
                    RelationDef relMatch = null;
                    for (RelationDef r : model.getRelations()) {
                        if (r.getFieldName().equalsIgnoreCase(ff) || (r.getFieldName() + "Id").equalsIgnoreCase(ff)) {
                            relMatch = r;
                            break;
                        }
                    }
                    if (relMatch != null) {
                        sb.append("            if (").append(ff).append(" != null) {\n");
                        sb.append("                predicates.add(cb.equal(root.get(\"").append(relMatch.getFieldName())
                                .append("\").get(\"id\"), ").append(ff).append("));\n");
                        sb.append("            }\n");
                    } else {
                        sb.append("            if (").append(ff).append(" != null) {\n");
                        sb.append("                predicates.add(cb.equal(root.get(\"").append(ff).append("\"), ")
                                .append(ff).append("));\n");
                        sb.append("            }\n");
                    }
                }
            }

            sb.append("            return cb.and(predicates.toArray(new Predicate[0]));\n");
            sb.append("        };\n\n");
            sb.append("        Page<").append(className).append("> entityPage = repository.findAll(spec, pageable);\n");
            sb.append("        return PageResponse.of(entityPage.map(mapper::toResponse));\n");
            sb.append("    }\n\n");

            // getById
            sb.append("    @Override\n");
            sb.append("    @Transactional(readOnly = true)\n");
            sb.append("    public ").append(className).append("Response getById(").append(idType).append(" id) {\n");
            sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
            sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className)
                    .append(" not found with id: \" + id));\n");
            sb.append("        return mapper.toResponse(entity);\n");
            sb.append("    }\n\n");

            // create
            sb.append("    @Override\n");
            sb.append("    public ").append(className).append("Response create(Create").append(className)
                    .append("Request request) {\n");
            sb.append("        ").append(className).append(" entity = mapper.toEntity(request);\n");
            for (RelationDef rel : model.getRelations()) {
                if (rel.getRelationType() == RelationType.MANY_TO_ONE
                        || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                    String target = rel.getTargetModel();
                    String relCap = TypeHelper.capitalize(rel.getFieldName());
                    sb.append("        if (request.").append(rel.getFieldName()).append("Id() != null) {\n");
                    sb.append("            ").append(target).append(" relEntity = ").append(target.toLowerCase())
                            .append("Repository.findById(request.").append(rel.getFieldName()).append("Id())\n");
                    sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(target)
                            .append(" not found with id: \" + request.").append(rel.getFieldName()).append("Id()));\n");
                    sb.append("            entity.set").append(relCap).append("(relEntity);\n");
                    sb.append("        }\n");
                }
            }
            sb.append("        ").append(className).append(" saved = repository.save(entity);\n");
            sb.append("        return mapper.toResponse(saved);\n");
            sb.append("    }\n\n");

            // update
            sb.append("    @Override\n");
            sb.append("    public ").append(className).append("Response update(").append(idType).append(" id, Update")
                    .append(className).append("Request request) {\n");
            sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
            sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className)
                    .append(" not found with id: \" + id));\n");
            sb.append("        mapper.updateEntity(entity, request);\n");
            for (RelationDef rel : model.getRelations()) {
                if (rel.getRelationType() == RelationType.MANY_TO_ONE
                        || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                    String target = rel.getTargetModel();
                    String relCap = TypeHelper.capitalize(rel.getFieldName());
                    sb.append("        if (request.").append(rel.getFieldName()).append("Id() != null) {\n");
                    sb.append("            ").append(target).append(" relEntity = ").append(target.toLowerCase())
                            .append("Repository.findById(request.").append(rel.getFieldName()).append("Id())\n");
                    sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(target)
                            .append(" not found with id: \" + request.").append(rel.getFieldName()).append("Id()));\n");
                    sb.append("            entity.set").append(relCap).append("(relEntity);\n");
                    sb.append("        }\n");
                }
            }
            sb.append("        ").append(className).append(" updated = repository.save(entity);\n");
            sb.append("        return mapper.toResponse(updated);\n");
            sb.append("    }\n\n");

            // delete
            sb.append("    @Override\n");
            sb.append("    public void delete(").append(idType).append(" id) {\n");
            sb.append("        if (!repository.existsById(id)) {\n");
            sb.append("            throw new ResourceNotFoundException(\"").append(className)
                    .append(" not found with id: \" + id);\n");
            sb.append("        }\n");
            sb.append("        repository.deleteById(id);\n");
            sb.append("    }\n\n");
        }

        // Custom endpoints with Phase 3 Logic Blocks
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String bodyType = custom.hasBody() ? custom.getBodyType() : null;
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            List<TypeHelper.PathVariable> vars = TypeHelper.extractPathVariables(custom.getPath(), model, idType,
                    custom);
            List<String> paramParts = new ArrayList<>();
            if (bodyType != null && !bodyType.isEmpty()) {
                paramParts.add(bodyType + " body");
            }
            boolean hasId = false;
            for (TypeHelper.PathVariable v : vars) {
                paramParts.add(v.getType() + " " + v.getName());
                if ("id".equalsIgnoreCase(v.getName())) {
                    hasId = true;
                }
            }
            String serviceParams = String.join(", ", paramParts);

            sb.append("    @Override\n");
            sb.append("    public ").append(ret).append(" ").append(methodName).append("(")
                    .append(serviceParams).append(") {\n");

            FieldDef matchingField = null;
            RelationDef matchingRel = null;
            String varName = null;

            if (hasId) {
                matchingField = model.getIdField() != null ? model.getIdField() : model.findField("id");
                varName = "id";
            } else if (!vars.isEmpty()) {
                TypeHelper.PathVariable targetVar = vars.get(0);
                varName = targetVar.getName();
                if (custom.getQuery() != null && !custom.getQuery().isEmpty()) {
                    String q = custom.getQuery().replaceAll("\\s+", "");
                    String fieldName = q.contains("==") ? q.split("==")[0] : q;
                    if (fieldName.contains(".")) {
                        fieldName = fieldName.split("\\.")[0];
                    }
                    matchingField = model.findField(fieldName);
                    if (matchingField == null) {
                        for (RelationDef r : model.getRelations()) {
                            if (r.getFieldName().equalsIgnoreCase(fieldName)
                                    || (r.getFieldName() + "Id").equalsIgnoreCase(fieldName)) {
                                matchingRel = r;
                                break;
                            }
                        }
                    }
                } else {
                    matchingField = model.findField(targetVar.getName());
                    if (matchingField == null) {
                        for (RelationDef r : model.getRelations()) {
                            String rName = r.getFieldName().toLowerCase();
                            String vName = targetVar.getName().toLowerCase();
                            if (vName.equals(rName) || vName.equals(rName + "id") || vName.equals("id" + rName)) {
                                matchingRel = r;
                                break;
                            }
                        }
                    }
                }
            }

            if (matchingRel != null) {
                if (!custom.getDescription().isEmpty()) {
                    sb.append("        // ").append(custom.getDescription().replace("\n", " ")).append("\n");
                }
                String relCap = TypeHelper.capitalize(matchingRel.getFieldName());
                if (ret.startsWith("List<")) {
                    sb.append("        return repository.findBy").append(relCap).append("Id(").append(varName)
                            .append(").stream()\n");
                    sb.append("                .map(mapper::toResponse)\n");
                    sb.append("                .toList();\n");
                } else {
                    sb.append("        return repository.findBy").append(relCap).append("Id(").append(varName)
                            .append(").stream()\n");
                    sb.append("                .findFirst()\n");
                    sb.append("                .map(mapper::toResponse)\n");
                    sb.append("                .orElse(null);\n");
                }
            } else if (matchingField != null) {
                if (!custom.getDescription().isEmpty()) {
                    sb.append("        // ").append(custom.getDescription().replace("\n", " ")).append("\n");
                }

                if (ret.startsWith("List<")) {
                    String fieldCap = TypeHelper.capitalize(matchingField.getName());
                    sb.append("        return repository.findBy").append(fieldCap).append("(").append(varName)
                            .append(").stream()\n");
                    sb.append("                .map(mapper::toResponse)\n");
                    sb.append("                .toList();\n");
                } else {
                    boolean isId = matchingField.hasAnnotation("id") || "id".equalsIgnoreCase(matchingField.getName());
                    String lookupMethod = isId ? "findById"
                            : ("findBy" + TypeHelper.capitalize(matchingField.getName()));

                    sb.append("        ").append(className).append(" entity = repository.").append(lookupMethod)
                            .append("(").append(varName).append(")\n");
                    sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className)
                            .append(" not found with ").append(matchingField.getName()).append(": \" + ")
                            .append(varName).append("));\n\n");

                    // 1. Guard conditions (skip role-only guards handled by @PreAuthorize at API
                    // layer)
                    for (var guard : custom.getGuards()) {
                        String entityCond = guard.getEntityCondition();
                        if (entityCond.isEmpty()) {
                            continue;
                        }
                        String javaCond = LogicTranspiler.transpileCondition(entityCond, model);
                        sb.append("        if (!(").append(javaCond).append(")) {\n");
                        sb.append("            throw new IllegalArgumentException(\"")
                                .append(guard.getErrorMessage().replace("\"", "\\\"")).append("\");\n");
                        sb.append("        }\n\n");
                    }

                    // 2. Action statements
                    String explicitReturnStmt = null;
                    if (!custom.getActionStatements().isEmpty()) {
                        boolean needsCurrentUser = false;
                        List<String> primaryModifications = new ArrayList<>();
                        List<String> secondaryOperations = new ArrayList<>();

                        for (String stmt : custom.getActionStatements()) {
                            if (stmt.contains("currentUser") || stmt.contains("currentUsername")) {
                                needsCurrentUser = true;
                            }
                            String t = stmt.trim();
                            if (t.startsWith("return ") || t.equals("return")) {
                                explicitReturnStmt = stmt;
                            } else if (t.startsWith("create ") || t.startsWith("update ") || t.matches("^notify\\s*\\(.*")) {
                                secondaryOperations.add(stmt);
                            } else {
                                primaryModifications.add(stmt);
                            }
                        }

                        if (needsCurrentUser) {
                            sb.append("        String currentUsername = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null\n")
                              .append("                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()\n")
                              .append("                : \"anonymous\";\n\n");
                        }
                        for (String stmt : primaryModifications) {
                            String javaStmt = LogicTranspiler.transpileStatement(stmt, model, file);
                            if (!javaStmt.isEmpty()) {
                                for (String line : javaStmt.split("\n")) {
                                    sb.append("        ").append(line.trim().isEmpty() ? "" : line).append("\n");
                                }
                            }
                        }
                        if (!primaryModifications.isEmpty()) {
                            sb.append("        entity = repository.save(entity);\n\n");
                        }

                        for (String stmt : secondaryOperations) {
                            String javaStmt = LogicTranspiler.transpileStatement(stmt, model, file);
                            if (!javaStmt.isEmpty()) {
                                for (String line : javaStmt.split("\n")) {
                                    sb.append("        ").append(line.trim().isEmpty() ? "" : line).append("\n");
                                }
                                sb.append("\n");
                            }
                        }

                        if (explicitReturnStmt != null) {
                            String javaStmt = LogicTranspiler.transpileStatement(explicitReturnStmt, model, file);
                            if (!javaStmt.isEmpty()) {
                                for (String line : javaStmt.split("\n")) {
                                    sb.append("        ").append(line.trim().isEmpty() ? "" : line).append("\n");
                                }
                            }
                        }
                    } else if (!custom.getDescription().isEmpty()) {
                        sb.append("        // Action: ").append(custom.getDescription().replace("\n", " "))
                                .append("\n");
                    }

                    if (explicitReturnStmt == null && !"void".equals(ret)) {
                        String mapCall = getMapperCall(ret, className, file);
                        if (mapCall.isEmpty()) {
                            sb.append("        return entity;\n");
                        } else {
                            sb.append("        return ").append(mapCall).append("(entity);\n");
                        }
                    }
                }
            } else if (!vars.isEmpty()) {
                TypeHelper.PathVariable targetVar = vars.get(0);
                sb.append("        // Note: Parameter '").append(targetVar.getName())
                        .append("' does not match an entity field on ").append(className)
                        .append(". Specify 'query: <field> == ").append(targetVar.getName())
                        .append("' to query explicitly.\n");
                if (!"void".equals(ret)) {
                    if (ret.startsWith("List<")) {
                        sb.append("        return java.util.Collections.emptyList();\n");
                    } else {
                        sb.append("        return null;\n");
                    }
                }
            } else {
                if (!custom.getDescription().isEmpty()) {
                    sb.append("        // ").append(custom.getDescription().replace("\n", " ")).append("\n");
                }
                if (!"void".equals(ret)) {
                    boolean isUserScoped = custom.isUserScoped(methodName);
                    RelationDef userRel = null;
                    ModelDef userModel = file.getUserModel();
                    if (file.hasAuth() && userModel != null) {
                        for (RelationDef r : model.getRelations()) {
                            if (r.getRelationType() == RelationType.MANY_TO_ONE
                                    && userModel.getName().equals(r.getTargetModel())) {
                                userRel = r;
                                break;
                            }
                        }
                    }

                    String methodRef = getMapperMethodRef(ret, className, file);

                    if (file.hasAuth() && isUserScoped && userRel != null) {
                        FieldDef idFieldDef = userModel.findField("email");
                        if (idFieldDef == null)
                            idFieldDef = userModel.findField("username");
                        if (idFieldDef == null)
                            idFieldDef = userModel.findField("name");
                        String idName = idFieldDef != null ? TypeHelper.capitalize(idFieldDef.getName()) : "Email";
                        String relCap = TypeHelper.capitalize(userRel.getFieldName());

                        sb.append(
                                "        String currentUsername = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();\n");
                        if (ret.startsWith("List<")) {
                            sb.append("        return repository.findBy").append(relCap).append(idName)
                                    .append("(currentUsername).stream()\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .toList();\n");
                        } else {
                            sb.append("        return repository.findBy").append(relCap).append(idName)
                                    .append("(currentUsername).stream()\n");
                            sb.append("                .findFirst()\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .orElse(null);\n");
                        }
                    } else if (file.hasAuth() && isUserScoped && userModel != null
                            && model.getName().equals(userModel.getName())) {
                        FieldDef idFieldDef = userModel.findField("email");
                        if (idFieldDef == null)
                            idFieldDef = userModel.findField("username");
                        if (idFieldDef == null)
                            idFieldDef = userModel.findField("name");
                        String idName = idFieldDef != null ? TypeHelper.capitalize(idFieldDef.getName()) : "Email";

                        sb.append(
                                "        String currentUsername = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();\n");
                        if (ret.startsWith("List<")) {
                            sb.append("        return repository.findBy").append(idName)
                                    .append("(currentUsername).stream()\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .toList();\n");
                        } else {
                            sb.append("        return repository.findBy").append(idName).append("(currentUsername)\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"")
                                    .append(className).append(" not found for user: \" + currentUsername));\n");
                        }
                    } else {
                        if (ret.startsWith("List<")) {
                            sb.append("        return repository.findAll().stream()\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .toList();\n");
                        } else {
                            sb.append("        return repository.findAll().stream()\n");
                            sb.append("                .findFirst()\n");
                            sb.append("                .map(").append(methodRef).append(")\n");
                            sb.append("                .orElse(null);\n");
                        }
                    }
                }
            }

            sb.append("    }\n\n");
        }

        if (model.hasStorageEndpoints()) {
            for (FieldDef field : model.getFields()) {
                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
                    String fieldName = field.getName();
                    String fieldCap = TypeHelper.capitalize(fieldName);
                    boolean isImage = field.hasAnnotation("image");
                    AnnotationDef ann = isImage ? field.getAnnotation("image") : field.getAnnotation("file");
                    String maxSize = ann != null && ann.getArgument("maxSize") != null ? ann.getArgument("maxSize").toString() : null;
                    Object typesObj = ann != null ? ann.getArgument("types") : null;
                    String path = ann != null && ann.getArgument("path") != null ? ann.getArgument("path").toString() : (model.getName().toLowerCase() + "/" + fieldName);

                    // upload
                    sb.append("    @Override\n");
                    sb.append("    public ").append(className).append("Response upload").append(fieldCap)
                            .append("(").append(idType).append(" id, MultipartFile file) {\n");
                    sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
                    sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n");

                    if (isImage) {
                        sb.append("        FileValidationRules rules = FileValidationRules.forImage(");
                    } else {
                        sb.append("        FileValidationRules rules = FileValidationRules.forFile(");
                    }
                    sb.append(maxSize != null ? "\"" + maxSize + "\"" : "null").append(", ");
                    if (typesObj instanceof List<?> list && !list.isEmpty()) {
                        sb.append("java.util.List.of(");
                        for (int i = 0; i < list.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append("\"").append(list.get(i)).append("\"");
                        }
                        sb.append(")");
                    } else {
                        sb.append("null");
                    }
                    sb.append(", \"").append(path).append("\");\n\n");

                    sb.append("        FileMetadata stored = storageService.upload(file, rules);\n");
                    sb.append("        if (entity.get").append(fieldCap).append("() != null) {\n");
                    sb.append("            storageService.delete(entity.get").append(fieldCap).append("());\n");
                    sb.append("        }\n");
                    sb.append("        entity.set").append(fieldCap).append("(stored.getFileKey());\n");
                    sb.append("        ").append(className).append(" saved = repository.save(entity);\n");
                    sb.append("        return mapper.toResponse(saved);\n");
                    sb.append("    }\n\n");

                    // download
                    sb.append("    @Override\n");
                    sb.append("    @Transactional(readOnly = true)\n");
                    sb.append("    public FileResource download").append(fieldCap).append("(").append(idType).append(" id) {\n");
                    sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
                    sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n");
                    sb.append("        if (entity.get").append(fieldCap).append("() == null) {\n");
                    sb.append("            throw new ResourceNotFoundException(\"No ").append(fieldName).append(" file uploaded for ").append(className).append(" with id: \" + id);\n");
                    sb.append("        }\n");
                    sb.append("        return storageService.download(entity.get").append(fieldCap).append("());\n");
                    sb.append("    }\n\n");

                    // delete
                    sb.append("    @Override\n");
                    sb.append("    public void delete").append(fieldCap).append("(").append(idType).append(" id) {\n");
                    sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
                    sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n");
                    sb.append("        if (entity.get").append(fieldCap).append("() != null) {\n");
                    sb.append("            storageService.delete(entity.get").append(fieldCap).append("());\n");
                    sb.append("            entity.set").append(fieldCap).append("(null);\n");
                    sb.append("            repository.save(entity);\n");
                    sb.append("        }\n");
                    sb.append("    }\n\n");
                }
            }
        }

        sb.append("}\n");

        return new JavaSourceFile(pkgDir + "impl/" + implName + ".java", sb.toString());
    }

    private static String getMapperCall(String ret, String className, ZenithFile file) {
        if (ret == null || "void".equals(ret)) return "";
        if (ret.equalsIgnoreCase(className + "Response")) {
            return "mapper.toResponse";
        }
        if (file != null && file.findDto(ret) != null) {
            return "mapper.to" + ret;
        }
        if (ret.equalsIgnoreCase(className)) {
            return "";
        }
        return "mapper.toResponse";
    }

    private static String getMapperMethodRef(String ret, String className, ZenithFile file) {
        String baseRet = ret;
        if (ret != null && ret.startsWith("List<") && ret.endsWith(">")) {
            baseRet = ret.substring(5, ret.length() - 1).trim();
        }
        if (file != null && file.findDto(baseRet) != null) {
            return "mapper::to" + baseRet;
        }
        return "mapper::toResponse";
    }

}