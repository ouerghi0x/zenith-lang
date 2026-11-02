package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ZenithFile {
    private final AppConfig config;
    private final List<EnumDef> enums;
    private final List<ModelDef> models;
    private final List<DtoDef> dtos;
    private final List<SeedDef> seeds;
    private final List<ImportDef> imports;
    private final boolean explicitConfig;

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<ImportDef> imports, boolean explicitConfig) {
        this.config = config != null ? config : AppConfig.defaults();
        this.enums = enums != null ? enums : new ArrayList<>();
        this.models = models != null ? models : new ArrayList<>();
        this.dtos = dtos != null ? dtos : new ArrayList<>();
        this.seeds = seeds != null ? seeds : new ArrayList<>();
        this.imports = imports != null ? imports : new ArrayList<>();
        this.explicitConfig = explicitConfig;
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<ImportDef> imports) {
        this(config, enums, models, dtos, seeds, imports, config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds) {
        this(config, enums, models, dtos, seeds, Collections.emptyList(), config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos) {
        this(config, enums, models, dtos, Collections.emptyList(), Collections.emptyList(), config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models) {
        this(config, enums, models, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), config != null);
    }

    public boolean hasExplicitConfig() {
        return explicitConfig;
    }

    public List<ImportDef> getImports() {
        return Collections.unmodifiableList(imports);
    }

    public AppConfig getConfig() {
        return config;
    }

    public List<SeedDef> getSeeds() {
        return Collections.unmodifiableList(seeds);
    }

    public List<EnumDef> getEnums() {
        return Collections.unmodifiableList(enums);
    }

    public List<ModelDef> getModels() {
        return Collections.unmodifiableList(models);
    }

    public List<DtoDef> getDtos() {
        return Collections.unmodifiableList(dtos);
    }

    public ModelDef findModel(String name) {
        for (ModelDef model : models) {
            if (model.getName().equalsIgnoreCase(name)) {
                return model;
            }
        }
        return null;
    }

    public EnumDef findEnum(String name) {
        for (EnumDef e : enums) {
            if (e.getName().equalsIgnoreCase(name)) {
                return e;
            }
        }
        return null;
    }

    public ModelDef getUserModel() {
        String configured = config.getString("userModel", null);
        if (configured != null) {
            ModelDef m = findModel(configured);
            if (m != null)
                return m;
        }
        for (ModelDef m : models) {
            for (FieldDef f : m.getFields()) {
                if (f.hasAnnotation("password")) {
                    return m;
                }
            }
        }
        return findModel("User");
    }

    public boolean hasAuth() {
        return config.isAuthEnabled() && getUserModel() != null;
    }
}
