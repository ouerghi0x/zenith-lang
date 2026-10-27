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

    private static JavaSourceFile generateInterface(ModelDef model, ZenithFile file, String pkgDir, String basePkg, String className) {
        String serviceName = className + "Service";
        FieldDef idField = model.getIdField();
        String idType = idField != null ? TypeHelper.toSimpleJavaType(idField.getType()) : "Long";

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".service;\n\n");

        sb.append("import ").append(basePkg).append(".dto.*;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("public interface ").append(serviceName).append(" {\n");
        sb.append("    PageResponse<").append(className).append("Response> getAll(int page, int size, String sort, String direction, String search);\n");
        sb.append("    ").append(className).append("Response getById(").append(idType).append(" id);\n");
        sb.append("    ").append(className).append("Response create(Create").append(className).append("Request request);\n");
        sb.append("    ").append(className).append("Response update(").append(idType).append(" id, Update").append(className).append("Request request);\n");
        sb.append("    void delete(").append(idType).append(" id);\n");

        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            sb.append("    ").append(ret).append(" ").append(methodName).append("(").append(idType).append(" id);\n");
        }

        sb.append("}\n");

        return new JavaSourceFile(pkgDir + serviceName + ".java", sb.toString());
    }

    private static JavaSourceFile generateImplementation(ModelDef model, ZenithFile file, String pkgDir, String basePkg, String className) {
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
        sb.append("import ").append(basePkg).append(".exception.ResourceNotFoundException;\n");

        // Relation repository imports
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("import ").append(basePkg).append(".repository.").append(rel.getTargetModel()).append("Repository;\n");
                sb.append("import ").append(basePkg).append(".entity.").append(rel.getTargetModel()).append(";\n");
            }
        }

        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.transaction.annotation.Transactional;\n");
        sb.append("import org.springframework.data.domain.Page;\n");
        sb.append("import org.springframework.data.domain.PageRequest;\n");
        sb.append("import org.springframework.data.domain.Pageable;\n");
        sb.append("import org.springframework.data.domain.Sort;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@Service\n");
        sb.append("@Transactional\n");
        sb.append("public class ").append(implName).append(" implements ").append(serviceName).append(" {\n\n");

        sb.append("    private final ").append(repoName).append(" repository;\n");
        sb.append("    private final ").append(mapperName).append(" mapper;\n");

        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("    private final ").append(rel.getTargetModel()).append("Repository ").append(rel.getTargetModel().toLowerCase()).append("Repository;\n");
            }
        }
        sb.append("\n");

        // Constructor injection
        sb.append("    public ").append(implName).append("(\n");
        sb.append("        ").append(repoName).append(" repository,\n");
        sb.append("        ").append(mapperName).append(" mapper");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append(",\n        ").append(rel.getTargetModel()).append("Repository ").append(rel.getTargetModel().toLowerCase()).append("Repository");
            }
        }
        sb.append("\n    ) {\n");
        sb.append("        this.repository = repository;\n");
        sb.append("        this.mapper = mapper;\n");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                sb.append("        this.").append(rel.getTargetModel().toLowerCase()).append("Repository = ").append(rel.getTargetModel().toLowerCase()).append("Repository;\n");
            }
        }
        sb.append("    }\n\n");

        // getAll
        sb.append("    @Override\n");
        sb.append("    @Transactional(readOnly = true)\n");
        sb.append("    public PageResponse<").append(className).append("Response> getAll(int page, int size, String sort, String direction, String search) {\n");
        sb.append("        Sort sortObj = \"desc\".equalsIgnoreCase(direction) ? Sort.by(sort).descending() : Sort.by(sort).ascending();\n");
        sb.append("        Pageable pageable = PageRequest.of(page, size, sortObj);\n");
        sb.append("        Page<").append(className).append("> entityPage;\n");

        CrudConfig crud = model.getCrudConfig();
        if (crud != null && !crud.getSearchFields().isEmpty()) {
            sb.append("        if (search != null && !search.trim().isEmpty()) {\n");
            sb.append("            entityPage = repository.search(search.trim(), pageable);\n");
            sb.append("        } else {\n");
            sb.append("            entityPage = repository.findAll(pageable);\n");
            sb.append("        }\n");
        } else {
            sb.append("        entityPage = repository.findAll(pageable);\n");
        }
        sb.append("        return PageResponse.of(entityPage.map(mapper::toResponse));\n");
        sb.append("    }\n\n");

        // getById
        sb.append("    @Override\n");
        sb.append("    @Transactional(readOnly = true)\n");
        sb.append("    public ").append(className).append("Response getById(").append(idType).append(" id) {\n");
        sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
        sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n");
        sb.append("        return mapper.toResponse(entity);\n");
        sb.append("    }\n\n");

        // create
        sb.append("    @Override\n");
        sb.append("    public ").append(className).append("Response create(Create").append(className).append("Request request) {\n");
        sb.append("        ").append(className).append(" entity = mapper.toEntity(request);\n");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String target = rel.getTargetModel();
                String relCap = TypeHelper.capitalize(rel.getFieldName());
                sb.append("        if (request.").append(rel.getFieldName()).append("Id() != null) {\n");
                sb.append("            ").append(target).append(" relEntity = ").append(target.toLowerCase()).append("Repository.findById(request.").append(rel.getFieldName()).append("Id())\n");
                sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(target).append(" not found with id: \" + request.").append(rel.getFieldName()).append("Id()));\n");
                sb.append("            entity.set").append(relCap).append("(relEntity);\n");
                sb.append("        }\n");
            }
        }
        sb.append("        ").append(className).append(" saved = repository.save(entity);\n");
        sb.append("        return mapper.toResponse(saved);\n");
        sb.append("    }\n\n");

        // update
        sb.append("    @Override\n");
        sb.append("    public ").append(className).append("Response update(").append(idType).append(" id, Update").append(className).append("Request request) {\n");
        sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
        sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n");
        sb.append("        mapper.updateEntity(entity, request);\n");
        for (RelationDef rel : model.getRelations()) {
            if (rel.getRelationType() == RelationType.MANY_TO_ONE || rel.getRelationType() == RelationType.ONE_TO_ONE) {
                String target = rel.getTargetModel();
                String relCap = TypeHelper.capitalize(rel.getFieldName());
                sb.append("        if (request.").append(rel.getFieldName()).append("Id() != null) {\n");
                sb.append("            ").append(target).append(" relEntity = ").append(target.toLowerCase()).append("Repository.findById(request.").append(rel.getFieldName()).append("Id())\n");
                sb.append("                .orElseThrow(() -> new ResourceNotFoundException(\"").append(target).append(" not found with id: \" + request.").append(rel.getFieldName()).append("Id()));\n");
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
        sb.append("            throw new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id);\n");
        sb.append("        }\n");
        sb.append("        repository.deleteById(id);\n");
        sb.append("    }\n\n");

        // Custom endpoints with Phase 3 Logic Blocks
        for (CustomEndpointDef custom : model.getCustomEndpoints()) {
            String methodName = TypeHelper.toEndpointMethodName(custom.getMethod(), custom.getPath());
            String ret = "Void".equalsIgnoreCase(custom.getReturnType()) ? "void" : custom.getReturnType();
            sb.append("    @Override\n");
            sb.append("    public ").append(ret).append(" ").append(methodName).append("(").append(idType).append(" id) {\n");
            sb.append("        ").append(className).append(" entity = repository.findById(id)\n");
            sb.append("            .orElseThrow(() -> new ResourceNotFoundException(\"").append(className).append(" not found with id: \" + id));\n\n");

            // 1. Guard conditions
            for (var guard : custom.getGuards()) {
                String javaCond = LogicTranspiler.transpileCondition(guard.getExpression(), model);
                sb.append("        if (!(").append(javaCond).append(")) {\n");
                sb.append("            throw new IllegalArgumentException(\"").append(guard.getErrorMessage().replace("\"", "\\\"")).append("\");\n");
                sb.append("        }\n\n");
            }

            // 2. Action statements
            if (!custom.getActionStatements().isEmpty()) {
                for (String stmt : custom.getActionStatements()) {
                    String javaStmt = LogicTranspiler.transpileStatement(stmt, model);
                    if (!javaStmt.isEmpty()) {
                        sb.append("        ").append(javaStmt).append("\n");
                    }
                }
                sb.append("        entity = repository.save(entity);\n\n");
            } else if (!custom.getDescription().isEmpty()) {
                sb.append("        // Action: ").append(custom.getDescription().replace("\n", " ")).append("\n");
            }

            if (!"void".equals(ret)) {
                sb.append("        return mapper.toResponse(entity);\n");
            }
            sb.append("    }\n\n");
        }

        sb.append("}\n");

        return new JavaSourceFile(pkgDir + "impl/" + implName + ".java", sb.toString());
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
