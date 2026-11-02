package dev.zenith.analyzer;

import dev.zenith.ast.*;

import java.util.*;

public class SemanticAnalyzer {

    public ZenithFile analyze(ZenithFile file) {
        Set<String> modelNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();
        Set<String> dtoNames = new HashSet<>();

        for (DtoDef d : file.getDtos()) {
            if (!dtoNames.add(d.getName())) {
                throw new AnalysisException("Duplicate dto defined: '" + d.getName() + "'");
            }
            Set<String> dtoFieldNames = new HashSet<>();
            for (FieldDef f : d.getFields()) {
                if (!dtoFieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException("Duplicate field '" + f.getName() + "' in dto '" + d.getName() + "'");
                }
            }
        }

        for (EnumDef e : file.getEnums()) {
            if (!enumNames.add(e.getName())) {
                throw new AnalysisException("Duplicate enum defined: '" + e.getName() + "'");
            }
        }

        for (ModelDef m : file.getModels()) {
            if (!modelNames.add(m.getName())) {
                throw new AnalysisException("Duplicate model defined: '" + m.getName() + "'");
            }
        }

        List<ModelDef> validatedModels = new ArrayList<>();
        Map<String, List<FieldDef>> modelFieldsMap = new HashMap<>();
        for (ModelDef model : file.getModels()) {
            List<FieldDef> fields = new ArrayList<>(model.getFields());
            Set<String> fieldNames = new HashSet<>();

            boolean hasId = false;
            for (FieldDef f : fields) {
                if (!fieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException(
                            "Duplicate field '" + f.getName() + "' in model '" + model.getName() + "'");
                }
                if (f.hasAnnotation("id")) {
                    hasId = true;
                }
            }

            // Auto-inject id if omitted
            if (!hasId) {
                List<AnnotationDef> idAnns = new ArrayList<>();
                idAnns.add(new AnnotationDef("id"));
                idAnns.add(new AnnotationDef("auto"));
                FieldDef autoId = new FieldDef("id", TypeRef.simple("Long"), false, idAnns, null);
                fields.add(0, autoId);
            }

            // Validate @file and @image field types
            for (FieldDef field : fields) {
                if (field.hasAnnotation("file") || field.hasAnnotation("image")) {
                    String tName = field.getType().getName();
                    if (!"String".equalsIgnoreCase(tName)) {
                        throw new AnalysisException("Field '" + field.getName() + "' in model '" + model.getName()
                                + "' annotated with @" + (field.hasAnnotation("image") ? "image" : "file")
                                + " must be of type String (to store file key/URL)");
                    }
                }
            }

            // Validate relations
            for (RelationDef rel : model.getRelations()) {
                if (!modelNames.contains(rel.getTargetModel())) {
                    throw new AnalysisException("Model '" + model.getName()
                            + "' references unknown relation target model '" + rel.getTargetModel() + "'");
                }
            }

            // Validate crud search/filter/sort fields
            CrudConfig crud = model.getCrudConfig();
            if (crud != null) {
                for (String sf : crud.getSearchFields()) {
                    if (findFieldCaseInsensitive(fields, sf) == null) {
                        throw new AnalysisException("Search field '" + sf + "' in crud config does not exist on model '"
                                + model.getName() + "'");
                    }
                }
                for (String ff : crud.getFilterFields()) {
                    if (findFieldCaseInsensitive(fields, ff) == null && !isRelation(model.getRelations(), ff)) {
                        throw new AnalysisException(
                                "Filter field '" + ff + "' does not exist on model '" + model.getName() + "'");
                    }
                }
            }

            // Validate custom endpoint bodies & action model references
            for (CustomEndpointDef endpoint : model.getCustomEndpoints()) {
                if (endpoint.hasBody()) {
                    String bt = endpoint.getBodyType();
                    if (!dtoNames.contains(bt) && !modelNames.contains(bt)) {
                        throw new AnalysisException("Endpoint '" + endpoint.getMethod() + " " + endpoint.getPath()
                                + "' in model '" + model.getName() + "' references unknown request body type '" + bt
                                + "'");
                    }
                }
                for (String stmt : endpoint.getActionStatements()) {
                    String t = stmt.trim();
                    if (t.startsWith("create ") || t.startsWith("update ")) {
                        String kw = t.startsWith("create ") ? "create" : "update";
                        String rest = t.substring(kw.length() + 1).trim();
                        int space = rest.indexOf(' ');
                        int brace = rest.indexOf('{');
                        int end = space > 0 ? (brace > 0 ? Math.min(space, brace) : space) : (brace > 0 ? brace : rest.length());
                        String targetModel = rest.substring(0, end).trim();
                        if (!targetModel.isEmpty() && !modelNames.contains(targetModel)) {
                            throw new AnalysisException("Action '" + kw + "' in endpoint '" + endpoint.getMethod() + " "
                                    + endpoint.getPath() + "' references unknown model '" + targetModel + "'");
                        }
                    }
                }
            }

            validatedModels.add(new ModelDef(model.getName(), fields, model.getRelations(), model.getCrudConfig(),
                    model.getCustomEndpoints()));
            modelFieldsMap.put(model.getName(), fields);
        }
        for (SeedDef seed : file.getSeeds()) {
            ModelDef targetModel = null;
            for (ModelDef m : validatedModels) {
                if (m.getName().equalsIgnoreCase(seed.getEntityName())) {
                    targetModel = m;
                    break;
                }
            }
            if (targetModel == null) {
                throw new AnalysisException("Seed references unknown model '" + seed.getEntityName() + "'");
            }
            for (Map<String, Object> row : seed.getRows()) {
                for (String key : row.keySet()) {
                    FieldDef f = targetModel.findField(key);
                    RelationDef r = targetModel.findRelation(key);
                    if (f == null && r == null) {
                        throw new AnalysisException("Seed for model '" + targetModel.getName() + "' references unknown field '" + key + "'");
                    }
                    Object val = row.get(key);
                    if (val instanceof SeedRef) {
                        SeedRef ref = (SeedRef) val;
                        ModelDef refModel = null;
                        for (ModelDef m : validatedModels) {
                            if (m.getName().equalsIgnoreCase(ref.getModelName())) {
                                refModel = m;
                                break;
                            }
                        }
                        if (refModel == null) {
                            throw new AnalysisException("Seed reference points to unknown model '" + ref.getModelName() + "' in " + targetModel.getName() + "." + key);
                        }
                        if (r != null && !r.getTargetModel().equalsIgnoreCase(ref.getModelName())) {
                            throw new AnalysisException("Seed reference model '" + ref.getModelName() + "' does not match relation target '" + r.getTargetModel() + "' on field '" + key + "'");
                        }
                    }
                }
            }
        }
        return new ZenithFile(file.getConfig(), file.getEnums(), validatedModels, file.getDtos(), file.getSeeds(),
                file.getImports(), file.hasExplicitConfig());
    }

    private FieldDef findFieldCaseInsensitive(List<FieldDef> fields, String name) {
        for (FieldDef f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    private boolean isRelation(List<RelationDef> relations, String name) {
        for (RelationDef r : relations) {
            if (r.getFieldName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
