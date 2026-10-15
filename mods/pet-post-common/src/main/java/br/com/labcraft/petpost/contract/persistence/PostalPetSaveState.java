package br.com.labcraft.petpost.contract.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/** API-independent persistent state. Adapters decode NBT or Codec data; this contract validates schema versions and dimension/position keys before the next write. */
public record PostalPetSaveState(int schemaVersion, Map<String, PostRecord> posts) {
    public static final String SCHEMA_VERSION_FIELD = "schemaVersion";
    public static final int LEGACY_SCHEMA_VERSION = 0;
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PostalPetSaveState {
        validateReadableVersion(schemaVersion);
        posts = Map.copyOf(new LinkedHashMap<>(posts));
        posts.forEach((key, post) -> {
            if (!key.equals(post.storageKey())) {
                throw new IllegalArgumentException("Chave persistida deve ser dimensão|posição");
            }
        });
    }

    /** Data without an explicit version belongs to legacy schema 0. */
    public static PostalPetSaveState legacy(Map<String, PostRecord> posts) {
        return new PostalPetSaveState(LEGACY_SCHEMA_VERSION, posts);
    }

    /** New data is always written using the current logical schema. */
    public static PostalPetSaveState current(Map<String, PostRecord> posts) {
        return new PostalPetSaveState(CURRENT_SCHEMA_VERSION, posts);
    }

    /** Prepares a valid snapshot, including legacy data, for the next write. */
    public PostalPetSaveState forWrite() {
        return current(posts);
    }

    public static int readVersion(boolean hasExplicitVersion, int storedVersion) {
        int version = hasExplicitVersion ? storedVersion : LEGACY_SCHEMA_VERSION;
        validateReadableVersion(version);
        return version;
    }

    private static void validateReadableVersion(int version) {
        if (version < LEGACY_SCHEMA_VERSION || version > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion não suportada: " + version);
        }
    }
}
