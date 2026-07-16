package ru.obabok.client;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.HudAlignment;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.*;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import ru.obabok.client.util.ChunkScheduler;
import ru.obabok.common.References;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Config implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = References.MOD_ID + ".json";
    private static final String GENERIC_KEY = References.MOD_ID + ".config.generic";
    public static class Generic
    {
        public static final ConfigBoolean JOIN_NOTIFICATION = new ConfigBoolean("joinNotification", false).apply(GENERIC_KEY);
        public static final ConfigInteger UNLOADED_CHUNK_MAX_DISTANCE = new ConfigInteger("unloadedChunkMaxDistance", 500).apply(GENERIC_KEY);
        public static final ConfigInteger SELECTED_BLOCKS_MAX_DISTANCE = new ConfigInteger("selectedBlocksMaxDistance", -1).apply(GENERIC_KEY);

        public static final ConfigInteger UNLOADED_CHUNK_Y_OFFSET = new ConfigInteger("unloadedChunkYOffset", -50).apply(GENERIC_KEY);
        //public static final ConfigFloat UNLOADED_CHUNK_SCALE = new ConfigFloat("unloadedChunkScale", 4.0f, 0.1f, 20f).apply(GENERIC_KEY);

        public static final ConfigColor UNLOADED_CHUNK_COLOR = new ConfigColor("unloadedChunkColor", "#ffffec59").apply(GENERIC_KEY);
        public static final ConfigColor SELECTED_BLOCKS_COLOR = new ConfigColor("selectedBlocksColor", "#AAD71B1B").apply(GENERIC_KEY);
        public static final ConfigColor AREA_EDGE_COLOR = new ConfigColor("areaEdgeColor", "#30FFFFFF").apply(GENERIC_KEY);

        public static final ConfigBoolean AREA_EDGE_RENDER = new ConfigBoolean("areaEdgeRender", false).apply(GENERIC_KEY);
        //public static final ConfigBoolean OLD_BLOCK_RENDER = new ConfigBoolean("oldBlockRender", true).apply(GENERIC_KEY);
        //public static final ConfigBoolean OLD_CHUNK_RENDER = new ConfigBoolean("oldChunkRender", true).apply(GENERIC_KEY);

        public static final ConfigInteger PROCESS_COOLDOWN = new ConfigInteger("processCooldown", 10, 1, 100).apply(GENERIC_KEY);
        //public static final ConfigBoolean RENDER_PROCESS_QUEUE = new ConfigBoolean("renderProcessQueue", false).apply(GENERIC_KEY);

        public static final ConfigBoolean REALTIME_UPDATE = new ConfigBoolean("realtimeUpdate", false).apply(GENERIC_KEY);
        public static final ConfigInteger LOD1 = new ConfigInteger("LOD1", 30).apply(GENERIC_KEY);
        public static final ConfigInteger LOD2 = new ConfigInteger("LOD2", 50).apply(GENERIC_KEY);
        public static final ConfigInteger LOD2_HORIZON = new ConfigInteger("LOD2_horizon", 100).apply(GENERIC_KEY);
        public static final ConfigHotkey LOOK_RANDOM_SELECTED_BLOCK = new ConfigHotkey("lookRandomSelectedBlock", "").apply(GENERIC_KEY);
        public static final ConfigBooleanHotkeyed MAIN_RENDER = new ConfigBooleanHotkeyed("mainRender", true, "").apply(GENERIC_KEY);
        public static final ConfigHotkey MAIN = new ConfigHotkey("mainHotkey", "LEFT_ALT,RIGHT_BRACKET").apply(GENERIC_KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                JOIN_NOTIFICATION,
                UNLOADED_CHUNK_MAX_DISTANCE,
                SELECTED_BLOCKS_MAX_DISTANCE,
                LOD1,
                LOD2,
                LOD2_HORIZON,

                UNLOADED_CHUNK_Y_OFFSET,
                //UNLOADED_CHUNK_SCALE,

                UNLOADED_CHUNK_COLOR,
                SELECTED_BLOCKS_COLOR,
                AREA_EDGE_COLOR,

                AREA_EDGE_RENDER,
                //OLD_BLOCK_RENDER,
                //OLD_CHUNK_RENDER,

                PROCESS_COOLDOWN,
                //RENDER_PROCESS_QUEUE,
                REALTIME_UPDATE,
                LOOK_RANDOM_SELECTED_BLOCK,
                MAIN_RENDER,
                MAIN
        );
    }

    private static final String HUD_KEY = References.MOD_ID+".config.hud";
    public static class Hud
    {
        public static final ConfigBooleanHotkeyed HUD_ENABLE = new ConfigBooleanHotkeyed("hudEnable", true, "").apply(HUD_KEY);
        public static final ConfigInteger HUD_POS_X = new ConfigInteger("hudPosX", 0).apply(HUD_KEY);
        public static final ConfigInteger HUD_POS_Y = new ConfigInteger("hudPosY", 0).apply(HUD_KEY);
        public static final ConfigOptionList HUD_ALIGNMENT = new ConfigOptionList("hudAlignment", HudAlignment.BOTTOM_RIGHT).apply(HUD_KEY);
        public static final ConfigFloat HUD_SCALE = new ConfigFloat("hudScale", 1.0f, 0.1f, 10f).apply(HUD_KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                HUD_ENABLE,
                HUD_POS_X,
                HUD_POS_Y,
                HUD_ALIGNMENT,
                HUD_SCALE
        );
    }

    public static final List<IHotkey> HOTKEYS = ImmutableList.of(Generic.MAIN, Generic.MAIN_RENDER, Generic.LOOK_RANDOM_SELECTED_BLOCK, Hud.HUD_ENABLE);
    @Override
    public void onConfigsChanged() {
        saveToFile();
        loadFromFile();
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }

    public void loadFromFile() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                JsonObject root = element.getAsJsonObject();

                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "HUD", Hud.OPTIONS);
                ChunkScheduler.updatePeriod(Generic.PROCESS_COOLDOWN.getIntegerValue());
            }
            else
            {
                References.LOGGER.error("loadFromFile(): Failed to load config file '{}'.", configFile.toAbsolutePath());
            }
        }
    }

    public void saveToFile() {
        Path dir = FileUtils.getConfigDirectory();

        if (!Files.exists(dir))
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir))
        {
            JsonObject root = new JsonObject();

            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hud", Hud.OPTIONS);

            JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
        }
        else
        {
            References.LOGGER.error("saveToFile(): Config Folder '{}' does not exist!", dir.toAbsolutePath());
        }
    }
}
