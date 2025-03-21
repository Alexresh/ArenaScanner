package ru.obabok.arenascanner.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ru.obabok.arenascanner.client.ArenascannerClient;
import ru.obabok.arenascanner.client.Models.Config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigurationManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ArenaScanner/config.json");

    public static Config loadConfig() {
        Config config = new Config();

        // Если файл конфигурации уже существует, загружаем его
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                ArenascannerClient.LOGGER.error(e.getMessage());
            }
        } else {
            // Если файл не существует, копируем его из ресурсов
            try {
                Path source = FabricLoader.getInstance().getModContainer(ArenascannerClient.MOD_ID).get().findPath("assets/arenascanner/config/config.json").get();
                CONFIG_FILE.mkdirs();
                Files.copy(source, CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return loadConfig();
            } catch (IOException e) {
                ArenascannerClient.LOGGER.error(e.getMessage());
            }
        }
        ChunkScheduler.updatePeriod(config.processChunkCooldown);
        return config;
    }


    public static void saveConfig(Config config) {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            ArenascannerClient.LOGGER.error(e.getMessage());
        }
    }
}
