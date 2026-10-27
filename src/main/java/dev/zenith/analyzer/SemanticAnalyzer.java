package dev.zenith.analyzer;

import dev.zenith.ast.*;

import java.util.*;

public class SemanticAnalyzer {

    public ZenithFile analyze(ZenithFile file) {
        Set<String> modelNames = new HashSet<>();
        Set<String> enumNames = new HashSet<>();

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

        for (ModelDef model : file.getModels()) {
            List<FieldDef> fields = new ArrayList<>(model.getFields());
            Set<String> fieldNames = new HashSet<>();

            boolean hasId = false;
            for (FieldDef f : fields) {
                if (!fieldNames.add(f.getName().toLowerCase())) {
                    throw new AnalysisException("Duplicate field '" + f.getName() + "' in model '" + model.getName() + "'");
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

            // Validate relations
            for (RelationDef rel : model.getRelations()) {
                if (!modelNames.contains(rel.getTargetModel())) {
                    throw new AnalysisException("Model '" + model.getName() + "' references unknown relation target model '" + rel.getTargetModel() + "'");
                }
            }

            // Validate crud search/filter/sort fields
            CrudConfig crud = model.getCrudConfig();
            if (crud != null) {
                for (String sf : crud.getSearchFields()) {
                    if (findFieldCaseInsensitive(fields, sf) == null) {
                        throw new AnalysisException("Search field '" + sf + "' in crud config does not exist on model '" + model.getName() + "'");
                    }
                }
                for (String ff : crud.getFilterFields()) {
                    if (findFieldCaseInsensitive(fields, ff) == null && !isRelation(model.getRelations(), ff)) {
                        throw new AnalysisException("Filter field '" + ff + "' does not exist on model '" + model.getName() + "'");
                    }
                }
            }

            validatedModels.add(new ModelDef(model.getName(), fields, model.getRelations(), model.getCrudConfig(), model.getCustomEndpoints()));
        }

        return new ZenithFile(file.getConfig(), file.getEnums(), validatedModels);
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
