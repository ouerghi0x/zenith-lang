package dev.zenith.extension;

import java.util.Map;

/**
 * Service Provider Interface (SPI) for Zenith Extensions.
 * 
 * Community Edition runs with no PRO provider on classpath.
 * When a commercial 'zenith-pro' JAR or extension module is added to classpath,
 * it registers an implementation of this interface via java.util.ServiceLoader.
 */
public interface ZenithProExtension {

    /**
     * Unique identifier for the extension (e.g. "zenith-pro-enterprise").
     */
    String getExtensionId();

    /**
     * Display name (e.g. "Zenith Studio PRO").
     */
    String getDisplayName();

    /**
     * Returns true if a valid commercial license is detected and active.
     */
    boolean isLicensed();

    /**
     * Name/organization the license was issued to.
     */
    String getLicenseHolder();

    /**
     * Expiration date in ISO-8601 (or "Perpetual").
     */
    String getLicenseExpiry();

    /**
     * Hook to register extra REST API routes on Zenith Studio Server.
     */
    void registerStudioRoutes(Object studioServer);

    /**
     * Metadata properties surfaced to the Studio frontend (e.g. unlocked features).
     */
    Map<String, Object> getProFeaturesMetadata();
}
