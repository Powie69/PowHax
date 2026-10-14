package powie.powhax.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import powie.powhax.modules.PriceScraper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static powie.powhax.Powhax.LOG;

public class Config {
    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON = new GsonBuilder().create();

    private static final Path CONFIG_FOLDER = FabricLoader.getInstance().getGameDir().resolve("powhax");
    private static final Path SHOP_FOLDER = CONFIG_FOLDER.resolve("shop");
    private static final Path BEDROCK_FOLDER = CONFIG_FOLDER.resolve("bedrock");
    private static final Path MAP_FOLDER = CONFIG_FOLDER.resolve("map");
    // @formatter:off
    private static final Type MAPS_TYPE = new TypeToken<HashSet<Integer>>() {}.getType();
    // @formatter:on

    public static void initializeConfig() {
        try {
            Files.createDirectories(CONFIG_FOLDER);
            Files.createDirectories(SHOP_FOLDER);
            Files.createDirectories(BEDROCK_FOLDER);
            Files.createDirectories(MAP_FOLDER);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config folder", e);
        }
    }

    // shop

    public static Path writeNewShopData(Map<String, PriceScraper.ItemForSale> itemsForSale) {
        Path file = nextAvailableFile(LocalDate.now(), Utils.getWorldName(), SHOP_FOLDER, "json");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            GSON_PRETTY.toJson(itemsForSale, writer);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write shop data to " + file, e);
        }
        LOG.info("Wrote shop data to: {}", file);
        return file;
    }

    // bedrock

    public static Path newBedrockFile() {
        return nextAvailableFile(LocalDate.now(), Utils.getWorldName(), BEDROCK_FOLDER, "txt");
    }

    // mapart

    public static Set<Integer> getMapFile(String worldName) {
        try {
            Path file = MAP_FOLDER.resolve(worldName + ".json");
            if (!Files.exists(file)) {
                Files.createFile(file);
                return new HashSet<>();
            }
            String content = Files.readString(file).trim();
            if (content.isEmpty()) return new HashSet<>();

            Set<Integer> result = GSON.fromJson(content, MAPS_TYPE);
            return result != null ? result : new HashSet<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read maps config", e);
        } catch (JsonParseException e) {
            LOG.error("Failed to parse map config", e);
            return new HashSet<>();
        }
    }

    public static void saveMapFile(String worldName, Set<Integer> maps) {
        try {
            Path file = MAP_FOLDER.resolve(worldName + ".json");
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(maps, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write maps config", e);
        }
    }

    private static Path nextAvailableFile(LocalDate date, String worldName, Path folder, String extension) {
        String safeWorldName = worldName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String datePrefix = date.format(DateTimeFormatter.ISO_LOCAL_DATE);

        int index = 1;
        Path file;
        do {
            file = folder.resolve("%s_%s_%d.%s".formatted(datePrefix, safeWorldName, index++, extension));
        } while (Files.exists(file));
        return file;
    }
}
