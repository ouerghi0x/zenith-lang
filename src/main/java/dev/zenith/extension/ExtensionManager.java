package dev.zenith.extension;

import java.util.*;

/**
 * Discovers and manages loaded Zenith extensions via Java ServiceLoader.
 */
public class ExtensionManager {

    private static final ExtensionManager INSTANCE = new ExtensionManager();
    private ZenithProExtension proExtension = null;
    private boolean initialized = false;

    private ExtensionManager() {}

    public static ExtensionManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        ServiceLoader<ZenithProExtension> loader = ServiceLoader.load(ZenithProExtension.class);
        Iterator<ZenithProExtension> it = loader.iterator();
        if (it.hasNext()) {
            this.proExtension = it.next();
        }
    }

    public boolean isProAvailable() {
        initialize();
        return proExtension != null;
    }

    public boolean isProLicensed() {
        initialize();
        return proExtension != null && proExtension.isLicensed();
    }

    public ZenithProExtension getProExtension() {
        initialize();
        return proExtension;
    }

    public String getEditionName() {
        if (isProLicensed()) {
            return "Zenith PRO Edition (" + proExtension.getLicenseHolder() + ")";
        } else if (isProAvailable()) {
            return "Zenith PRO Edition (Unlicensed / Trial)";
        }
        return "Zenith Community Edition";
    }

    public Map<String, Object> getEditionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        boolean pro = isProLicensed();
        status.put("isPro", pro);
        status.put("edition", pro ? "PRO" : "COMMUNITY");
        status.put("editionName", getEditionName());
        if (proExtension != null) {
            status.put("licenseHolder", proExtension.getLicenseHolder());
            status.put("licenseExpiry", proExtension.getLicenseExpiry());
            status.put("features", proExtension.getProFeaturesMetadata());
        } else {
            status.put("licenseHolder", null);
            status.put("licenseExpiry", null);
            status.put("features", Collections.emptyMap());
        }
        return status;
    }
}

