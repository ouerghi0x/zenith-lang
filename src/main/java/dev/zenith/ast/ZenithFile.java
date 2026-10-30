package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ZenithFile {
    private final AppConfig config;
    private final List<EnumDef> enums;
    private final List<ModelDef> models;

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models) {
        this.config = config != null ? config : AppConfig.defaults();
        this.enums = enums != null ? enums : new ArrayList<>();
        this.models = models != null ? models : new ArrayList<>();
    }

    public AppConfig getConfig() {
        return config;
    }

    public List<EnumDef> getEnums() {
        return Collections.unmodifiableList(enums);
    }

    public List<ModelDef> getModels() {
        return Collections.unmodifiableList(models);
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
            if (m != null) return m;
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
