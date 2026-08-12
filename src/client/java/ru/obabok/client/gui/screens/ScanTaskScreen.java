package ru.obabok.client.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import ru.obabok.common.model.BlockArea;
import ru.obabok.client.models.ScreenPlus;
import ru.obabok.client.network.ClientNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class ScanTaskScreen extends ScreenPlus {
    private final Screen parent;
    private ToggelableWidgedDropDownList<String> whitelistSelectorList;
    private final BlockArea initialBox;
    private EditBox minX, minY, minZ;
    private EditBox maxX, maxY, maxZ;
    private final List<CoordButton> coordButtons = new ArrayList<>();
    private EditBox shareNameField;
    private Button startButton;

    public static int selectedBoxIndex = 0;
    private StringWidget regionLabel;

    protected ScanTaskScreen(Screen parent) {
        super(Component.literal("Scan task screen"));
        this.parent = parent;
        if(Minecraft.getInstance().player == null) Minecraft.getInstance().setScreenAndShow(parent);
        BlockArea existing = Scan.getArea();
        if (existing == null || existing.getBoxes().isEmpty()) {
            this.initialBox = new BlockArea(new BlockBox(
                    Minecraft.getInstance().player.getOnPos(),
                    Minecraft.getInstance().player.getOnPos()
            ));
            Scan.setArea(initialBox);
        } else {
            this.initialBox = existing;
        }
        selectedBoxIndex = 0;
    }

    public ScanTaskScreen(Screen parent, BlockArea range){
        super(Component.literal("Scan task screen"));
        this.parent = parent;
        this.initialBox = range;
        Scan.setArea(initialBox);
    }

    @Override
    public void init() {
        super.init();

        int x = 20;
        int y = 60;
        int rowHeight = 30;

        int listY = 20;
        //regions
        addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
            if (selectedBoxIndex > 0) {
                selectedBoxIndex--;
                refreshFieldsFromSelection();
            }
        }).bounds(20, listY, 20, 20).build());

        regionLabel = new StringWidget(45, listY, 80, 20, Component.empty(), font);
        addRenderableWidget(regionLabel);

        addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
            BlockArea area = Scan.getArea();
            if (area != null && selectedBoxIndex < area.getBoxes().size() - 1) {
                selectedBoxIndex++;
                refreshFieldsFromSelection();
            }
        }).bounds(130, listY, 20, 20).build());

        Button addBtn = Button.builder(Component.literal("+ Add"), btn -> {
            BlockArea area = Scan.getArea();
            if (area != null) {
                BlockPos playerPos = minecraft.player != null ? minecraft.player.getOnPos() : BlockPos.ZERO;
                area.add(new BlockBox(playerPos, playerPos));
                selectedBoxIndex = area.getBoxes().size() - 1;
                refreshFieldsFromSelection();
            }
        }).bounds(160, listY, 50, 20).build();
        addBtn.active = !Scan.isProcessing();
        addRenderableWidget(addBtn);

        Button delBtn = Button.builder(Component.literal("- Del"), btn -> {
            BlockArea area = Scan.getArea();
            if (area != null && area.getBoxes().size() > 1) {
                area.remove(selectedBoxIndex);
                selectedBoxIndex = Math.min(selectedBoxIndex, area.getBoxes().size() - 1);
                refreshFieldsFromSelection();
            }
        }).bounds(215, listY, 50, 20).build();
        delBtn.active = !Scan.isProcessing();
        addRenderableWidget(delBtn);

        //Corner1
        addRenderableWidget(new StringWidget(x, y, 100, 20, Component.literal("Corner 1"), font));

        x+=20;
        //X
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("X:"), font));
        minX = createField(x, y, initialBox.getArea(0).min().getX());
        Button btnMinX = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinX, minX));
        //Y
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Y:"), font));
        minY = createField(x, y, initialBox.getArea(0).min().getY());
        Button btnMinY = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinY, minY));
        //Z
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Z:"), font));
        minZ = createField(x, y, initialBox.getArea(0).min().getZ());
        Button btnMinZ = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMinZ, minZ));

        Button moveToPlayer1 = Button.builder(Component.literal("Move to player"), btn -> moveToPlayer(true)).bounds(x - 15, y + 30, 90, 20).build();
        moveToPlayer1.active = !Scan.isProcessing();
        addRenderableWidget(moveToPlayer1);

        x += 100;
        y = 60;

        //Corner2
        addRenderableWidget(new StringWidget(x, y, 100, 20, Component.literal("Corner 2"), font));
        x+=20;
        //maxX
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("X:"), font));
        maxX = createField(x, y, initialBox.getArea(0).max().getX());
        Button btnMaxX = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxX, maxX));

        //maxY
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Y:"), font));
        maxY = createField(x, y, initialBox.getArea(0).max().getY());
        Button btnMaxY = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxY, maxY));

        //maxZ
        y+=rowHeight;
        addRenderableWidget(new StringWidget(x - 15, y, 15, 20, Component.literal("Z:"), font));
        maxZ = createField(x, y, initialBox.getArea(0).max().getZ());
        Button btnMaxZ = createNudgeButton(x + 60, y);
        coordButtons.add(new CoordButton(btnMaxZ, maxZ));

        y+=rowHeight;
        Button moveToPlayer2 = Button.builder(Component.literal("Move to player"), btn -> moveToPlayer(false)).bounds(x - 15, y, 90, 20).build();
        moveToPlayer2.active = !Scan.isProcessing();
        addRenderableWidget(moveToPlayer2);

        //Whitelist text
        y+=rowHeight;
        addRenderableWidget(new StringWidget(20, y, 40, 20, Component.literal("Whitelist"), font));

        y+=rowHeight;
        //create whitelists selector
        List<String> whitelistFiles = WhitelistSelectorScreen.getWhitelistFilenames();
        ArrayList<String> list = new ArrayList<>(whitelistFiles);

        whitelistSelectorList = new ToggelableWidgedDropDownList<>(20,y,160,18,200,10, list);
        whitelistSelectorList.setZLevel(100);
        whitelistSelectorList.active = !Scan.isProcessing();
        addWidget(whitelistSelectorList);
        if(Scan.getCurrentFilename() != null){
            whitelistSelectorList.setSelectedEntry(Scan.getCurrentFilename());
        }


        y+=rowHeight;
        int shareY = Math.min(y, height - 90);
        addRenderableWidget(new StringWidget(20, shareY, 80, 20, Component.literal("Share name"), font));
        shareNameField = new EditBox(font, 20, shareY + 15, 160, 18, Component.empty());
        shareNameField.active = !Scan.isProcessing();
        addRenderableWidget(shareNameField);

        //back button
        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreenAndShow(parent)).bounds( 30, height - 30, 50, 20).build());

        //start button
        startButton = Button.builder(Component.literal("Start scan"), btn -> {
            try {
                String whitelistName = whitelistSelectorList.getSelectedEntry();
                String shareName = shareNameField.getValue().trim();
                if(shareName.isEmpty()){
                    Scan.executeAsync(minecraft.level, Scan.getArea(), whitelistSelectorList.getSelectedEntry());
                }else{
                    if(!ClientNetwork.requestScan(Scan.getArea(), whitelistName, shareName)){
                        Scan.executeAsync(minecraft.level, Scan.getArea(), whitelistSelectorList.getSelectedEntry());
                    }
                }

                this.onClose();
            }catch (Exception ignored){}
        }).bounds( 90, height - 30, 80, 20).build();
        startButton.active = whitelistSelectorList.getSelectedEntry() != null && !whitelistSelectorList.getSelectedEntry().isEmpty() && Scan.getArea() != null && !Scan.isProcessing();
        addRenderableWidget(startButton);

        Button stopScanBtn = Button.builder(Component.literal("Stop scan"), btn ->{
            BlockArea box = Scan.getArea();
            if (Scan.isRemoteProcessing()) {
                ClientNetwork.stopScan();
            }
            Scan.stopScan();
            minecraft.setScreenAndShow(new ScanTaskScreen(parent, box));
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
                minecraft.player.sendOverlayMessage(Component.literal(Scan.loadState() ? "State loaded" : "State didn't load"));
            }
            this.onClose();
        }).bounds(370, height - 30, 90, 20).build();
        loadStateBtn.active = !Scan.isProcessing();
        addRenderableWidget(loadStateBtn);

        refreshFieldsFromSelection();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {

    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if(keyEvent.key() == InputConstants.KEY_ESCAPE){
            minecraft.setScreenAndShow(parent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void refreshFieldsFromSelection() {
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) return;
        selectedBoxIndex = Math.clamp(selectedBoxIndex, 0, area.getBoxes().size() - 1);

        BlockBox box = area.getBoxes().get(selectedBoxIndex);

        minX.setValue(String.valueOf(box.min().getX()));
        minY.setValue(String.valueOf(box.min().getY()));
        minZ.setValue(String.valueOf(box.min().getZ()));
        maxX.setValue(String.valueOf(box.max().getX()));
        maxY.setValue(String.valueOf(box.max().getY()));
        maxZ.setValue(String.valueOf(box.max().getZ()));
        regionLabel.setMessage(Component.literal("Region " + (selectedBoxIndex + 1) + "/" + area.getBoxes().size()));
    }
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int y = height - 110;
        int x = 40;
        if(Scan.isProcessing()){
            context.text(font, Component.literal("Scan in process..."), x, y, CommonColors.WHITE);
            context.text(font, Component.literal("All chunks: " + Scan.getAllChunksCounter()), x, y += 15, CommonColors.WHITE);
            context.text(font, Component.literal("Unchecked chunks: " + Scan.unloadedChunks.size()), x, y += 15, CommonColors.WHITE);
            context.text(font, Component.literal("Percent left: " + String.format("%.2f", (double) Scan.unloadedChunks.size() / Scan.getAllChunksCounter() * 100)), x, y+=15, CommonColors.WHITE);
            context.text(font, Component.literal("Selected blocks: " +  Scan.selectedBlocks.size()), x, y+=15, CommonColors.WHITE);
        }else{
            context.text(font, Component.literal("Scan is stopped"), x, y, CommonColors.WHITE);
        }

        startButton.active = whitelistSelectorList.getSelectedEntry() != null && !whitelistSelectorList.getSelectedEntry().isEmpty() && Scan.getArea() != null && !Scan.isProcessing();
        context.outline(15,55, 110, 150, CommonColors.LIGHT_GRAY);
        context.outline(135,55, 110, 150, CommonColors.LIGHT_GRAY);
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
        super.extractRenderState(context, mouseX, mouseY, delta);
    }


    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {

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
        BlockArea area = Scan.getArea();
        if (area == null || area.getBoxes().isEmpty()) return;

        int minXv = parseInt(minX.getValue());
        int minYv = parseInt(minY.getValue());
        int minZv = parseInt(minZ.getValue());
        int maxXv = parseInt(maxX.getValue());
        int maxYv = parseInt(maxY.getValue());
        int maxZv = parseInt(maxZ.getValue());

        BlockBox updated = new BlockBox(
                new BlockPos(Math.min(minXv, maxXv), Math.min(minYv, maxYv), Math.min(minZv, maxZv)),
                new BlockPos(Math.max(minXv, maxXv), Math.max(minYv, maxYv), Math.max(minZv, maxZv))
        );

        area.setBox(selectedBoxIndex, updated);
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
