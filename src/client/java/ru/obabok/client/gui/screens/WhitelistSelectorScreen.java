package ru.obabok.client.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.obabok.client.util.WhitelistManager;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class WhitelistSelectorScreen extends Screen {
    private final Screen parent;
    private final int currentPage;
    private static final int ENTRIES_PER_PAGE = 12;

    public WhitelistSelectorScreen(Screen parent, int page) {
        super(Component.translatable("areascanner.gui.title.whitelists"));
        this.parent = parent;
        this.currentPage = Math.max(0, page);
    }
    public static List<String> getWhitelistFilenames() {
        File dir = new File(WhitelistManager.stringWhitelistsPath);
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .map(File::getName)
                .collect(Collectors.toList());
    }

    @Override
    protected void init() {
        List<String> whitelistFiles = getWhitelistFilenames();
        int totalPages = (whitelistFiles.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE;

        int from = currentPage * ENTRIES_PER_PAGE;
        int to = Math.min(from + ENTRIES_PER_PAGE, whitelistFiles.size());
        List<String> pageFiles = whitelistFiles.subList(from, to);

        int y = 30;
        // Поле для создания нового вайтлиста
        EditBox newWhitelistField = new EditBox(font, width / 2 - 100, y, 150, 20, Component.empty());
        addRenderableWidget(newWhitelistField);

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreenAndShow(parent))
                .bounds( 30, height - 30, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Create"), button -> {
            String name = newWhitelistField.getValue().trim();
            if (!name.isEmpty()) {
                if(WhitelistManager.createWhitelist(name) && minecraft.player != null){
                    minecraft.player.sendOverlayMessage(Component.literal("Created").withStyle(ChatFormatting.GOLD));
                    minecraft.setScreenAndShow(new WhitelistSelectorScreen(parent, 0));
                }
            }
        }).bounds(width / 2 + 55, y, 60, 20).build());


        y += 25;
        for (String filename : pageFiles) {
            String displayName = filename.replaceFirst("\\.json", "");
            addRenderableWidget(Button.builder(Component.literal(displayName), button -> {
                minecraft.setScreenAndShow(new WhitelistEditorScreen(this, filename, 0));
            }).bounds(width / 2 - 100, y, 150, 20).build());

            addRenderableWidget(Button.builder(Component.literal("❌"), button -> {
                if(WhitelistManager.deleteWhitelist(filename) && minecraft.player != null) {
                    minecraft.player.sendOverlayMessage(Component.literal("Deleted").withStyle(ChatFormatting.GOLD));
                    minecraft.setScreenAndShow(new WhitelistSelectorScreen(parent, currentPage));
                }
            }).bounds(width / 2 + 55, y, 20, 20).build());

            y += 23;
        }
        int buttonY = height - 60;
        if (currentPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), btn ->{
                minecraft.setScreenAndShow(new WhitelistSelectorScreen(parent, currentPage - 1));
            }).bounds(width / 2 - 120, buttonY, 80, 20).build());
        }

        addRenderableWidget(new StringWidget(
                width / 2 - 40, buttonY,
                80, 20,
                Component.literal("Page " + (currentPage + 1) + "/" + Math.max(1, totalPages)), font));

        if (to < whitelistFiles.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), btn ->{
                    minecraft.setScreenAndShow(new WhitelistSelectorScreen(parent, currentPage + 1));
            }).bounds(width / 2 + 40, buttonY, 80, 20).build());
        }

    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        //renderBackground(guiGraphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

}
