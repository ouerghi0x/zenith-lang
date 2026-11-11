package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ZenithFile {
    private final AppConfig config;
    private final List<EnumDef> enums;
    private final List<ModelDef> models;
    private final List<DtoDef> dtos;
    private final List<SeedDef> seeds;
    private final List<JobDef> jobs;
    private final List<MailDef> mails;
    private final List<SocketDef> sockets;
    private final List<ImportDef> imports;
    private final boolean explicitConfig;

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<JobDef> jobs, List<MailDef> mails, List<SocketDef> sockets, List<ImportDef> imports, boolean explicitConfig) {
        this.config = config != null ? config : AppConfig.defaults();
        this.enums = enums != null ? enums : new ArrayList<>();
        this.models = models != null ? models : new ArrayList<>();
        this.dtos = dtos != null ? dtos : new ArrayList<>();
        this.seeds = seeds != null ? seeds : new ArrayList<>();
        this.jobs = jobs != null ? jobs : new ArrayList<>();
        this.mails = mails != null ? mails : new ArrayList<>();
        this.sockets = sockets != null ? sockets : new ArrayList<>();
        this.imports = imports != null ? imports : new ArrayList<>();
        this.explicitConfig = explicitConfig;
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<JobDef> jobs, List<MailDef> mails, List<ImportDef> imports, boolean explicitConfig) {
        this(config, enums, models, dtos, seeds, jobs, mails, Collections.emptyList(), imports, explicitConfig);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<JobDef> jobs, List<ImportDef> imports, boolean explicitConfig) {
        this(config, enums, models, dtos, seeds, jobs, Collections.emptyList(), imports, explicitConfig);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<ImportDef> imports, boolean explicitConfig) {
        this(config, enums, models, dtos, seeds, Collections.emptyList(), imports, explicitConfig);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds, List<ImportDef> imports) {
        this(config, enums, models, dtos, seeds, Collections.emptyList(), imports, config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos,
            List<SeedDef> seeds) {
        this(config, enums, models, dtos, seeds, Collections.emptyList(), Collections.emptyList(), config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models, List<DtoDef> dtos) {
        this(config, enums, models, dtos, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), config != null);
    }

    public ZenithFile(AppConfig config, List<EnumDef> enums, List<ModelDef> models) {
        this(config, enums, models, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), config != null);
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

    public boolean hasCache() {
        if (config != null && config.hasCache()) return true;
        for (ModelDef m : models) {
            if (m.getCrudConfig() != null && m.getCrudConfig().hasCache()) return true;
            for (CustomEndpointDef ep : m.getCustomEndpoints()) {
                if (ep.hasCache()) return true;
            }
        }
        return false;
    }

    public List<JobDef> getJobs() {
        return Collections.unmodifiableList(jobs);
    }

    public boolean hasJobs() {
        return jobs != null && !jobs.isEmpty();
    }

    public List<MailDef> getMails() {
        return Collections.unmodifiableList(mails);
    }

    public boolean hasMails() {
        return (mails != null && !mails.isEmpty()) || (config != null && config.hasMail());
    }

    public MailDef findMail(String name) {
        if (name == null) return null;
        for (MailDef m : mails) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    public List<SocketDef> getSockets() {
        return Collections.unmodifiableList(sockets);
    }

    public boolean hasSockets() {
        return sockets != null && !sockets.isEmpty();
    }

    public SocketDef findSocket(String name) {
        if (name == null) return null;
        for (SocketDef s : sockets) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
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

    public DtoDef findDto(String name) {
        if (name == null) return null;
        for (DtoDef d : dtos) {
            if (d.getName().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
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

    public boolean hasStorage() {
        if (config.getProperties().containsKey("storage")) {
            Object s = config.getProperties().get("storage");
            if (s instanceof Boolean && (Boolean) s) return true;
            if (s instanceof String && !"none".equalsIgnoreCase((String) s) && !"false".equalsIgnoreCase((String) s)) return true;
        }
        for (ModelDef m : models) {
            for (FieldDef f : m.getFields()) {
                if (f.hasAnnotation("file") || f.hasAnnotation("image")) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasRateLimiting() {
        if (config != null && config.hasRateLimit()) {
            return true;
        }
        for (ModelDef m : models) {
            if (m.getCrudConfig() != null && m.getCrudConfig().hasRateLimit()) {
                return true;
            }
            for (CustomEndpointDef cep : m.getCustomEndpoints()) {
                if (cep.hasRateLimit()) {
                    return true;
                }
            }
        }
        return false;
    }

    public String getDefaultRole() {
        ModelDef userModel = getUserModel();
        if (userModel != null) {
            for (FieldDef f : userModel.getFields()) {
                if (f.hasAnnotation("role") || f.getName().equalsIgnoreCase("role") || f.getName().toLowerCase().endsWith("role")) {
                    if (f.getDefaultValue() != null) {
                        return f.getDefaultValue().toString();
                    }
                    EnumDef enumDef = findEnum(f.getType().getName());
                    if (enumDef != null && !enumDef.getValues().isEmpty()) {
                        for (String v : enumDef.getValues()) {
                            if (!v.toUpperCase().contains("ADMIN")) {
                                return v;
                            }
                        }
                        return enumDef.getValues().get(0);
                    }
                }
            }
        }
        for (EnumDef e : enums) {
            if (e.getName().toLowerCase().contains("role")) {
                for (String val : e.getValues()) {
                    if (val.equalsIgnoreCase("USER") || val.equalsIgnoreCase("CUSTOMER") || val.equalsIgnoreCase("PATIENT") || val.equalsIgnoreCase("MEMBER")) {
                        return val;
                    }
                }
                for (String v : e.getValues()) {
                    if (!v.toUpperCase().contains("ADMIN")) {
                        return v;
                    }
                }
                if (!e.getValues().isEmpty()) return e.getValues().get(0);
            }
        }
        return "USER";
    }

    public String getDefaultAdminRole() {
        for (EnumDef e : enums) {
            for (String val : e.getValues()) {
                if (val.equalsIgnoreCase("ADMIN") || val.equalsIgnoreCase("ADMINISTRATOR") || val.equalsIgnoreCase("SUPERADMIN") || val.equalsIgnoreCase("SUPER_ADMIN")) {
                    return val;
                }
            }
        }
        return "ADMIN";
    }

    public Set<String> getAllDefinedRoles() {
        Set<String> roles = new LinkedHashSet<>();
        ModelDef userModel = getUserModel();
        if (userModel != null) {
            for (FieldDef f : userModel.getFields()) {
                if (f.hasAnnotation("role") || f.getName().equalsIgnoreCase("role") || f.getName().toLowerCase().endsWith("role")) {
                    EnumDef ed = findEnum(f.getType().getName());
                    if (ed != null) {
                        roles.addAll(ed.getValues());
                    }
                }
            }
        }
        for (EnumDef e : enums) {
            if (e.getName().toLowerCase().contains("role")) {
                roles.addAll(e.getValues());
            }
        }
        return roles;
    }
}
