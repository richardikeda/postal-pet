package br.com.labcraft.petpost;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuração simples e autocorrigida do Pet Post, salva em JSON para permitir
 * ajustes do servidor sem recompilar o mod.
 */
public final class PetPostConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("pet-post.json");
    private static PetPostConfig instance = new PetPostConfig();

    public int defaultRadius = 16;
    public int previewPoints = 64;
    public boolean announceOnJoin = true;

    private PetPostConfig() {}

    public static PetPostConfig get() { return instance; }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            if (Files.exists(CONFIG_PATH)) {
                PetPostConfig loaded = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), PetPostConfig.class);
                if (loaded != null) instance = loaded;
            }
            instance.validate();
            Files.writeString(CONFIG_PATH, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            PetPost.LOGGER.error("Nao foi possivel carregar a configuracao {}", CONFIG_PATH, exception);
            instance = new PetPostConfig();
        }
    }

    private void validate() {
        defaultRadius = PetPostBlock.closestSupportedRadius(defaultRadius);
        previewPoints = Math.max(16, Math.min(256, previewPoints));
    }
}
