package ru.obabok.client.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import ru.obabok.client.network.ClientNetwork;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MaterialListScreen extends Screen {
    private static final Object2IntOpenHashMap<Block> materialList = new Object2IntOpenHashMap<>();
    private final Screen parent;

    private static final int PADDING = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int ICON_SIZE = 18;
    private static final int SCROLL_BAR_WIDTH = 8;

    private static List<Block> sortedBlocks;
    private double scrollOffset = 0.0;
    private boolean isDraggingScrollBar = false;
    private double lastMouseY = 0;

    private int contentWidth;
    private int contentHeight;
    private int left, top;

    public static long jobId;

    public MaterialListScreen(Screen parent, long _jobId) {
        super(Component.literal("Material list"));
        this.parent = parent;
        if(_jobId != 0){
            jobId = _jobId;
            ClientNetwork.requestMaterialList(_jobId);
        }
        updateList(materialList);
    }

    @Override
    protected void init() {
        super.init();

        contentWidth = 300;


        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            minecraft.setScreen(parent);
        }).bounds(10, height - 30, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);


        contentHeight = Math.min(sortedBlocks.size() * ROW_HEIGHT + PADDING * 2, this.height - 60);
        left = (this.width - contentWidth) / 2;
        top = (this.height - contentHeight) / 2;


        int availableHeight = contentHeight - PADDING * 2;
        int visibleRows = Math.max(1, availableHeight / ROW_HEIGHT);
        int maxScroll = Math.max(0, sortedBlocks.size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Область контента
        int contentX = left + PADDING;
        int contentY = top + PADDING;
        int contentRight = left + contentWidth - PADDING - SCROLL_BAR_WIDTH;


        // Рисуем строки
        int startIndex = (int) scrollOffset;
        int endIndex = Math.min(startIndex + visibleRows + 1, sortedBlocks.size());

        for (int i = startIndex; i < endIndex; i++) {
            Block block = sortedBlocks.get(i);
            int count = materialList.getInt(block);
            int y = contentY + (i - startIndex) * ROW_HEIGHT;

            // Получаем ItemStack для отображения
            ItemStack stack = getDisplayStack(block);

            // Иконка
            context.renderFakeItem(stack, contentX, y);
            String name;
            if(stack.is(Items.AIR)){
                name = block.getName().getString();
            }else {
                name = Component.translatable(stack.getItem().getDescriptionId()).getString();
            }
            context.drawString(font, name, contentX + ICON_SIZE + 4, y + (ROW_HEIGHT - font.lineHeight) / 2, CommonColors.WHITE, false);

            // Количество
            String countStr = String.valueOf(count);
            int countWidth = font.width(countStr);
            context.drawString(font, countStr, contentRight - countWidth, y + (ROW_HEIGHT - font.lineHeight) / 2, CommonColors.YELLOW, false);
        }

        // Рамка вокруг контента
        context.renderOutline(left - 1, top - 1, contentWidth + 2, contentHeight + 2, 0xFFAAAAAA);

        // Полоса прокрутки
        if (sortedBlocks.size() > visibleRows) {
            int totalHeight = sortedBlocks.size() * ROW_HEIGHT;
            int visibleHeight = visibleRows * ROW_HEIGHT;
            int barPixels = Math.max(8, visibleHeight * visibleHeight / totalHeight);
            int scrollableHeight = visibleHeight - barPixels;

            // Позиция ползунка
            double ratio = maxScroll > 0 ? scrollOffset / maxScroll : 0.0;
            int barY = top + PADDING + (int) (ratio * scrollableHeight);


            // Фон полосы
            context.fill(left + contentWidth - SCROLL_BAR_WIDTH, top + PADDING,
                    left + contentWidth, top + PADDING + visibleHeight,
                    0xFF404040);
            // Ползунок
            context.fill(left + contentWidth - SCROLL_BAR_WIDTH, barY,
                    left + contentWidth, barY + barPixels,
                    isDraggingScrollBar ? 0xFFAAAAAA : 0xFF666666);
        }

        super.render(context, mouseX, mouseY, delta);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isPointInsideContent(mouseX, mouseY)) {
            int visibleRows = (contentHeight - PADDING * 2) / ROW_HEIGHT;
            if (sortedBlocks.size() > visibleRows) {
                scrollOffset -= verticalAmount;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderBlurredBackground(GuiGraphics guiGraphics) {

    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }


    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0 && isScrollBarHovered(event.x(), event.y())) {
            isDraggingScrollBar = true;
            lastMouseY = event.y();
            return true;
        }
        return super.mouseClicked(event, bl);
    }


    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (isDraggingScrollBar) {
            int visibleRows = (contentHeight - PADDING * 2) / ROW_HEIGHT;
            int maxScroll = Math.max(0, sortedBlocks.size() - visibleRows);
            if (maxScroll > 0) {
                double dy = event.y() - lastMouseY;
                double ratio = dy / 200;//(contentHeight - PADDING * 2);
                scrollOffset += ratio * maxScroll;
                lastMouseY = event.y();
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            isDraggingScrollBar = false;
        }
        return super.mouseReleased(event);
    }

    public static void updateList(Map<Block, Integer> blocks){
        sortedBlocks = new ArrayList<>(blocks.keySet());
        sortedBlocks.sort((a, b) -> {
            String nameA = BuiltInRegistries.BLOCK.getKey(a).toString();
            String nameB = BuiltInRegistries.BLOCK.getKey(b).toString();
            return nameA.compareTo(nameB);

        });
        blocks.forEach(materialList::addTo);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(event.key() == InputConstants.KEY_ESCAPE){
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }


    private ItemStack getDisplayStack(Block block) {
        if(BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(block)).isPresent()){
            return BuiltInRegistries.ITEM.get(BuiltInRegistries.BLOCK.getKey(block)).get().value().getDefaultInstance();
        }else return Items.AIR.getDefaultInstance();
    }

    private boolean isPointInsideContent(double x, double y) {
        return x >= left && x <= left + contentWidth - SCROLL_BAR_WIDTH &&
                y >= top && y <= top + contentHeight;
    }

    private boolean isScrollBarHovered(double x, double y) {
        return x >= left + contentWidth - SCROLL_BAR_WIDTH && x <= left + contentWidth &&
                y >= top && y <= top + contentHeight;
    }

    public static void clear(){
        materialList.clear();
        materialList.trim();
    }
    public static void addBlock(Block block, int count){
        materialList.addTo(block, count);
    }

}
