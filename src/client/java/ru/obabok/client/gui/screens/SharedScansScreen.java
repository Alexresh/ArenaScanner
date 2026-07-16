package ru.obabok.client.gui.screens;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.CommonColors;
import ru.obabok.client.network.ClientNetwork;
import ru.obabok.common.model.JobInfo;


import java.util.List;

@Environment(EnvType.CLIENT)
public class SharedScansScreen extends Screen {
    private final int currentPage;
    private static final int ENTRIES_PER_PAGE = 6;
    private final Screen parent;

    public SharedScansScreen(int page, Screen parent) {
        super(Component.literal("Shared scans"));
        this.parent = parent;
        this.currentPage = Math.max(0, page);
    }

    @Override
    protected void init() {
        ClientNetwork.requestSharedList();
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreenAndShow(parent))
                .bounds(30, height - 30, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), btn -> {
            refresh();
        }).bounds(width - 90, 10, 60, 20).build());
    }

    public void drawJobs(){
        List<JobInfo> scans = ClientNetwork.getJobList();

        int totalPages = (scans.size() + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE;
        int from = currentPage * ENTRIES_PER_PAGE;
        int to = Math.min(from + ENTRIES_PER_PAGE, scans.size());
        List<JobInfo> page = scans.subList(from, to);

        if (scans.isEmpty()) {
            addRenderableWidget(new StringWidget(width / 2 - 60, 40, 120, 20,
                    Component.literal("No shared scans"), font));
            return;
        }

        int y = 30;
        int rowHeight = 36;
        for (JobInfo info : page) {
            String title = " " + ((info.name() == null || info.name().isEmpty()) ? ""+info.id() : info.name());
            String owner = info.owner() == null ? "unknown" : info.owner();
            String progress = "Chunks: " + info.processedChunks() + "/" + info.totalChunks() + " Blocks: " + info.selectedBlocks();
            addRenderableWidget(new StringWidget(60, y, width - 120, 12,
                    Component.literal(info.completedScan() ? "✓" : "⏳").withStyle(info.completedScan() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)
                            .append(Component.literal(title).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (" + owner + ") ").withStyle(info.ownerUUID().equals(minecraft.player.getUUID()) ? ChatFormatting.GOLD : ChatFormatting.WHITE))
                            .append(Component.literal(progress).withStyle(ChatFormatting.WHITE)), font));
            String dimension = info.dimension() == null ? "unknown" : info.dimension();
            String whitelistName = info.whitelistName() == null ? "_" : info.whitelistName();
            String details = "name: " + whitelistName + " dim: " + dimension
                    + " range: " + formatRange(info);
            addRenderableWidget(new StringWidget(60, y + 12, width - 120, 12,
                    Component.literal(details).withStyle(ChatFormatting.GRAY), font));

            if(info.id() == ClientNetwork.getActiveJobId()){
                addRenderableWidget(Button.builder(Component.literal("Unsubscribe"),button -> {
                    ClientNetwork.unsubscribeFromScan(info.id());
                    refresh();
                }).bounds(width - 190, y, 70, 20).build());
            }

            Button subscribeBtn = Button.builder(
                            Component.literal("Subscribe"),
                            btn -> {
                                ClientNetwork.subscribeToScan(info);
                                refresh();
                            })
                    .bounds(width - 115, y, 60, 20).build();

            subscribeBtn.active = ClientNetwork.getActiveJobId() == 0 && ClientNetwork.getDebugServerPacketsQueue() <= 100;
            addRenderableWidget(subscribeBtn);

            addRenderableWidget(new Button.Builder(Component.literal("[M]"), button -> {
                minecraft.setScreenAndShow(new MaterialListScreen(this, info.id()));
            }).bounds(width - 50, y, 20, 20).build());

            if(info.ownerUUID().equals(minecraft.player.getUUID()) || minecraft.player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)){
                addRenderableWidget(Button.builder(Component.literal("X"), btn -> {
                    ClientNetwork.deleteScan(info.id());
                    refresh();
                }).bounds(width - 25, y, 20, 20).build());
            }

            y += rowHeight;
        }

        int buttonY = height - 60;
        if (currentPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), btn ->
                    ClientNetwork.openSharedScansScreen(parent, currentPage - 1)//client.setScreen(new SharedScansScreen(currentPage - 1))
            ).bounds(width / 2 - 120, buttonY, 80, 20).build());
        }

        addRenderableWidget(new StringWidget(
                width / 2 - 40, buttonY,
                80, 20,
                Component.literal("Page " + (currentPage + 1) + "/" + Math.max(1, totalPages)), font));

        if (to < scans.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), btn ->
                    ClientNetwork.openSharedScansScreen(parent, currentPage + 1)//client.setScreen(new SharedScansScreen(currentPage + 1))
            ).bounds(width / 2 + 40, buttonY, 80, 20).build());
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {

    }

    private void refresh(){
        ClientNetwork.requestSharedList();
        ClientNetwork.openSharedScansScreen(parent, currentPage);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFF);
        graphics.text(font, "Server packets queue: " + ClientNetwork.getDebugServerPacketsQueue(), 10, 10, CommonColors.WHITE, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }


    private String formatRange(JobInfo info) {
        if (info.range() == null) {
            return "unknown";
        }
        return info.range().min().getX() + "," + info.range().min().getY() + "," + info.range().min().getZ()
                + " -> "
                + info.range().max().getX() + "," + info.range().max().getY() + "," + info.range().max().getZ();
    }
}
