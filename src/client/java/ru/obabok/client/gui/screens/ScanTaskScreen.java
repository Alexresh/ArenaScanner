package ru.obabok.client.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.CommonColors;
import org.lwjgl.glfw.GLFW;
import ru.obabok.client.Scan;
import ru.obabok.client.gui.widgets.ToggelableWidgedDropDownList;
import ru.obabok.client.models.ScreenPlus;
import ru.obabok.client.network.ClientNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ScanTaskScreen extends ScreenPlus {
    private final Screen parent;
    private ToggelableWidgedDropDownList<String> whitelistSelectorList;
    private final BlockBox initialBox;
    private EditBox minX, minY, minZ;
    private EditBox maxX, maxY, maxZ;
    private final List<CoordButton> coordButtons = new ArrayList<>();
    private EditBox shareNameField;
    private Button startButton;

    protected ScanTaskScreen(Screen parent) {
        super(Component.literal("Scan task screen"));
        this.parent = parent;
        if(Minecraft.getInstance().player == null) Minecraft.getInstance().setScreen(parent);
        this.initialBox = Scan.getRange() == null ? new BlockBox(Minecraft.getInstance().player.getOnPos(), Minecraft.getInstance().player.getOnPos()) : Scan.getRange();
        if(Scan.getRange() != initialBox){
            Scan.setRange(initialBox);
        }
    }

    public ScanTaskScreen(Screen parent, BlockBox range){
        super(Component.literal("Scan task screen"));
        this.parent = parent;
        this.initialBox = range;
        Scan.setRange(initialBox);
    }

    @Override
    public void init() {
        super.init();

        //create whitelists selector
        List<String> whitelistFiles = WhitelistSelectorScreen.getWhitelistFilenames();
        ArrayList<String> list = new ArrayList<>(whitelistFiles);
        //list.add("WorldEater");

        whitelistSelectorList = new ToggelableWidgedDropDownList<>(20,200,160,18,200,10, list);
        whitelistSelectorList.setZLevel(100);
        whitelistSelectorList.active = !Scan.isProcessing();
        addWidget(whitelistSelectorList);
        if(Scan.getCurrentFilename() != null){
            whitelistSelectorList.setSelectedEntry(Scan.getCurrentFilename());
        }

        int x = 20;
        int y = 20;
        int rowHeight = 30;

        //Corner1
        addRenderableWidget(new StringWidget(x, y, 100, 20, Component.literal("Corner 1"), font));

        x+=20;
        //X
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("X:"), font));
        minX = createField(x, y, initialBox.min().getX());
        Button btnMinX = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinX, minX));
        //Y
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Y:"), font));
        minY = createField(x, y, initialBox.min().getY());
        Button btnMinY = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinY, minY));
        //Z
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Z:"), font));
        minZ = createField(x, y, initialBox.min().getZ());
        Button btnMinZ = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinZ, minZ));

        Button moveToPlayer1 = Button.builder(Component.literal("Move to player"), btn -> moveToPlayer(true)).bounds(x - 15, y + 30, 90, 20).build();
        moveToPlayer1.active = !Scan.isProcessing();
        addRenderableWidget(moveToPlayer1);

        x += 100;
        y = 20;

        //Corner2
        addRenderableWidget(new StringWidget(x, y, 100, 20, Component.literal("Corner 2"), font));
        x+=20;
        //maxX
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("X:"), font));
        maxX = createField(x, y, initialBox.max().getX());
        Button btnMaxX = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxX, maxX));

        //maxY
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Y:"), font));
        maxY = createField(x, y, initialBox.max().getY());
        Button btnMaxY = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxY, maxY));

        //maxZ
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Z:"), font));
        maxZ = createField(x, y, initialBox.max().getZ());
        Button btnMaxZ = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxZ, maxZ));

        Button moveToPlayer2 = Button.builder(Component.literal("Move to player"), btn -> moveToPlayer(false)).bounds(x - 15, y + 30, 90, 20).build();
        moveToPlayer2.active = !Scan.isProcessing();
        addRenderableWidget(moveToPlayer2);

        //Whitelist text
        addRenderableWidget(new StringWidget(20, 180, 40, 20, Component.literal("Whitelist"), font));

        int shareY = Math.min(230, height - 90);
        addRenderableWidget(new StringWidget(20, shareY, 80, 20, Component.literal("Share name"), font));
        shareNameField = new EditBox(font, 20, shareY + 15, 160, 18, Component.empty());
        shareNameField.active = !Scan.isProcessing();
        addRenderableWidget(shareNameField);

        //back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreen(parent)).bounds( 30, height - 30, 50, 20).build());

        //start button
        startButton = Button.builder(Component.literal("Start scan"), btn -> {
            try {
                String whitelistName = whitelistSelectorList.getSelectedEntry();
                String shareName = shareNameField.getValue().trim();
                if(shareName.isEmpty()){
                    Scan.executeAsync(minecraft.level, Scan.getRange(), whitelistSelectorList.getSelectedEntry());
                }else{
                    if(!ClientNetwork.requestScan(Scan.getRange(), whitelistName, shareName)){
                        Scan.executeAsync(minecraft.level, Scan.getRange(), whitelistSelectorList.getSelectedEntry());
                    }
                }

                this.onClose();
            }catch (Exception ignored){}
        }).bounds( 90, height - 30, 80, 20).build();
        startButton.active = whitelistSelectorList.getSelectedEntry() != null && !whitelistSelectorList.getSelectedEntry().isEmpty() && Scan.getRange() != null && !Scan.isProcessing();
        addRenderableWidget(startButton);

        //if(ScanCommand.getProcessing())


        Button stopScanBtn = Button.builder(Component.literal("Stop scan"), btn ->{
            BlockBox box = Scan.getRange();
            if (Scan.isRemoteProcessing()) {
                ClientNetwork.stopScan();
            }
            Scan.stopScan();
            minecraft.setScreen(new ScanTaskScreen(parent, box));
        }).bounds(180, height - 30, 80, 20).build();
        stopScanBtn.active = Scan.isProcessing() || Scan.isRemoteProcessing();
        addRenderableWidget(stopScanBtn);

        Button saveStateBtn = Button.builder(Component.literal("Save state"), btn -> {
            Scan.saveState();
            this.onClose();
        }).bounds(270, height - 30, 90, 20).build();
        saveStateBtn.active = Scan.isProcessing();
        addRenderableWidget(saveStateBtn);

        Button loadStateBtn = Button.builder(Component.literal("Load state"), btn -> {
            if(minecraft.player != null){
                minecraft.player.displayClientMessage(Component.literal(Scan.loadState() ? "State loaded" : "State didn't load"), true);
            }
            this.onClose();
        }).bounds(370, height - 30, 90, 20).build();
        loadStateBtn.active = !Scan.isProcessing();
        addRenderableWidget(loadStateBtn);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.key() == InputConstants.KEY_ESCAPE){
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int y = height - 110;
        int x = 40;
        if(Scan.isProcessing()){
            context.drawString(font, Component.literal("Scan in process..."), x, y, CommonColors.WHITE);
            context.drawString(font, Component.literal("All chunks: " + Scan.getAllChunksCounter()), x, y += 15, CommonColors.WHITE);
            context.drawString(font, Component.literal("Unchecked chunks: " + Scan.unloadedChunks.size()), x, y += 15, CommonColors.WHITE);
            context.drawString(font, Component.literal("Percent left: " + String.format("%.2f", (double) Scan.unloadedChunks.size() / Scan.getAllChunksCounter() * 100)), x, y+=15, CommonColors.WHITE);
            context.drawString(font, Component.literal("Selected blocks: " +  Scan.selectedBlocks.size()), x, y+=15, CommonColors.WHITE);
        }else{
            context.drawString(font, Component.literal("Scan is stopped"), x, y, CommonColors.WHITE);
        }

        startButton.active = whitelistSelectorList.getSelectedEntry() != null && !whitelistSelectorList.getSelectedEntry().isEmpty() && Scan.getRange() != null && !Scan.isProcessing();
        context.renderOutline(15,15, 110, 150, CommonColors.LIGHT_GRAY);
        context.renderOutline(135,15, 110, 150, CommonColors.LIGHT_GRAY);
        for (CoordButton entry : coordButtons) {
            if (entry.button.isMouseOver(mouseX, mouseY)) {
                List<Component> tooltip = List.of(
                        Component.literal("Left click: +1"),
                        Component.literal("Right click: -1"),
                        Component.literal("Scroll: adjust"),
                        Component.literal("Ctrl(±100) Shift(±10) Alt(±5)")
                );
                context.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
                break;
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {

    }


    private void moveToPlayer(boolean minCorner){
        if(minecraft.player == null) return;
        if(minCorner){
            minX.setValue("" + minecraft.player.getBlockX());
            minY.setValue("" + minecraft.player.getBlockY());
            minZ.setValue("" + minecraft.player.getBlockZ());
        }else{
            maxX.setValue("" + minecraft.player.getBlockX());
            maxY.setValue("" + minecraft.player.getBlockY());
            maxZ.setValue("" + minecraft.player.getBlockZ());
        }
        updateRange();
    }


    private EditBox createField(int x, int y, int value) {
        EditBox field = new EditBox(font, x, y, 60, 18, Component.empty());
        field.setValue(String.valueOf(value));
        field.active = !Scan.isProcessing();
        field.setResponder(s -> {
            try {
                Integer.parseInt(s);
                updateRange();
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(field);
        return field;
    }

    private Button createNudgeButton(int x, int y) {
        Button nudgeBtn = Button.builder(Component.literal("±"), button -> {})
                .bounds(x, y, 18, 18).build();
        nudgeBtn.active = !Scan.isProcessing();
        return addRenderableWidget(nudgeBtn);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        for (CoordButton entry : coordButtons) {
            if (entry.button.isMouseOver(event.x(), event.y())) {
                playClickSound();
                int amount = (event.button() == 0) ? 1 : (event.button() == 1 ? -1 : 0);
                if (amount != 0) {
                    applyNudge(entry.field, amount);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    private void playClickSound(){
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (CoordButton entry : coordButtons) {
            if (entry.button.isMouseOver(mouseX, mouseY)) {
                playClickSound();
                int amount = (int) Math.signum(verticalAmount);
                if (amount != 0) {
                    applyNudge(entry.field, amount);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void applyNudge(EditBox field, int direction) {
        int step = 1;
        if (hasControlDown()) step *= 100;
        if (hasShiftDown()) step *= 10;
        if (hasAltDown()) step *= 5;
        int amount = direction * step;

        try {
            int currentValue = Integer.parseInt(field.getValue());
            field.setValue(String.valueOf(currentValue + amount));
            updateRange();
        } catch (NumberFormatException ignored) {}
    }

    public static boolean hasControlDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                  || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean hasShiftDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean hasAltDown() {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private void updateRange() {
        int minXv = parseInt(minX.getValue());
        int minYv = parseInt(minY.getValue());
        int minZv = parseInt(minZ.getValue());
        int maxXv = parseInt(maxX.getValue());
        int maxYv = parseInt(maxY.getValue());
        int maxZv = parseInt(maxZ.getValue());

        int fMinX = Math.min(minXv, maxXv);
        int fMaxX = Math.max(minXv, maxXv);
        int fMinY = Math.min(minYv, maxYv);
        int fMaxY = Math.max(minYv, maxYv);
        int fMinZ = Math.min(minZv, maxZv);
        int fMaxZ = Math.max(minZv, maxZv);

        Scan.setRange(new BlockBox(new BlockPos(fMinX, fMinY, fMinZ), new BlockPos(fMaxX, fMaxY, fMaxZ)));
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class CoordButton {
        final Button button;
        final EditBox field;

        CoordButton(Button button, EditBox field) {
            this.button = button;
            this.field = field;
        }
    }
}
