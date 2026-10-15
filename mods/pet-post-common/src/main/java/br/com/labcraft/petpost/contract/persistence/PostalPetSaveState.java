package br.com.labcraft.petpost.contract.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Forma lógica e independente de API do estado persistente do Postal Pet.
 *
 * <p>Os adapters ainda decodificam NBT ou Codec. Este contrato fixa a
 * evolução de versão e protege o mapa dimensão+posição antes que o adapter
 * regrave o estado na versão atual.</p>
 */
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

    /** Dados sem campo de versão pertencem ao formato legado 0. */
    public static PostalPetSaveState legacy(Map<String, PostRecord> posts) {
        return new PostalPetSaveState(LEGACY_SCHEMA_VERSION, posts);
    }

    /** Dados novos sempre são gravados no formato lógico atual. */
    public static PostalPetSaveState current(Map<String, PostRecord> posts) {
        return new PostalPetSaveState(CURRENT_SCHEMA_VERSION, posts);
    }

    /** Converte uma leitura válida, inclusive a legada, para a próxima gravação. */
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
