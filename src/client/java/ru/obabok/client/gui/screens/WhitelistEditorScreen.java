package ru.obabok.client.gui.screens;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import net.minecraft.world.level.block.Blocks;
import ru.obabok.client.Scan;
import ru.obabok.client.gui.widgets.ToggelableWidgedDropDownList;
import ru.obabok.client.models.ScreenPlus;
import ru.obabok.client.util.WhitelistManager;
import ru.obabok.common.BlockMatcher;
import ru.obabok.common.model.Whitelist;
import ru.obabok.common.model.WhitelistItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhitelistEditorScreen extends ScreenPlus {

    Screen parent;
    private final ArrayList<WhitelistItem> current_whitelist;
    private final int currentPage;
    private static final int BLOCKS_PER_PAGE = 12;
    private EditBox blockInput;
    private final String filename;
    private final WhitelistItem createdWhitelistItem = new WhitelistItem(null, null, null, null);
    private Button addToWhitelistBtn;
    private static final List<String> waterloggedValues = List.of("true", "false");
    public static List<String> pistonBehaviorValues = Arrays.stream(Scan.PistonBehavior.values()).map(Enum::toString).toList();


    private ToggelableWidgedDropDownList<String> waterloggedWidget;
    private ToggelableWidgedDropDownList<String> pistonBehaviorWidget;
    private ToggelableWidgedDropDownList<String> comparisonOperatorsWidget;
    private ToggelableWidgedDropDownList<String> equalsOperatorsWidget;
    private EditBox blastResistanceValue;

    protected WhitelistEditorScreen(Screen parent, String filename, int page) {
        super(Component.literal("WhitelistEditorScreen"));
        this.parent = parent;
        this.currentPage = Math.max(0, page);
        this.filename = filename;
        Whitelist whitelist = WhitelistManager.loadData(filename);
        if(whitelist == null){
            whitelist = new Whitelist(new ArrayList<>());
            WhitelistManager.saveData(whitelist, filename);
        }
        current_whitelist = whitelist.whitelist;
    }

    @Override
    protected void init() {
        super.init();
        int totalPages = (current_whitelist.size() + BLOCKS_PER_PAGE - 1) / BLOCKS_PER_PAGE;
        int from = currentPage * BLOCKS_PER_PAGE;
        int to = Math.min(from + BLOCKS_PER_PAGE, current_whitelist.size());
        List<WhitelistItem> pageBlocks = current_whitelist.subList(from, to);

        int rowHeight = 60;
        int y = 30;

        //block input
        addRenderableWidget(new StringWidget(30, y, 120, 20, Component.literal("Block"), font));
        blockInput = new EditBox(font, 130, y, 100, 20, Component.empty());
        addRenderableWidget(blockInput);

        y+=rowHeight;
        //waterlogged
        addRenderableWidget(new StringWidget(30, y, 70, 20, Component.literal("Waterlogged"), font));
        waterloggedWidget = new ToggelableWidgedDropDownList<>(130, y, 50, 20, 60, 2, waterloggedValues);
        waterloggedWidget.setZLevel(100);
        addWidget(waterloggedWidget);

        y+=rowHeight;
        //pistonBehavior
        addRenderableWidget(new StringWidget(30, y, 120, 20, Component.literal("Piston behavior"), font));
        equalsOperatorsWidget = new ToggelableWidgedDropDownList<>(130, y, 50, 20, 60, 2, BlockMatcher.EQUALS_OPERATORS);
        equalsOperatorsWidget.setZLevel(100);
        addWidget(equalsOperatorsWidget);
        pistonBehaviorWidget = new ToggelableWidgedDropDownList<>(190, y, 70, 20, 60, 2, pistonBehaviorValues);
        pistonBehaviorWidget.setZLevel(100);
        addWidget(pistonBehaviorWidget);

        y+=rowHeight;
        //blastResistance
        addRenderableWidget(new StringWidget(30, y, 120, 20, Component.literal("Blast resistance"), font));
        comparisonOperatorsWidget = new ToggelableWidgedDropDownList<>(130, y, 50, 20, 100, 4, BlockMatcher.COMPARISON_OPERATORS);
        comparisonOperatorsWidget.setZLevel(100);
        addWidget(comparisonOperatorsWidget);
        blastResistanceValue = new EditBox(font, 190, y, 30, 20, Component.empty());
        blastResistanceValue.setResponder(s -> {

        });
        addRenderableWidget(blastResistanceValue);

        y = 30;
        int i = 0;
        for (WhitelistItem item : pageBlocks) {
            i++;
            addRenderableWidget(Button.builder(Component.literal("❌"), btn -> {
                WhitelistManager.removeFromWhitelist(filename, item);
                minecraft.setScreen(new WhitelistEditorScreen(parent, filename, currentPage));
            }).bounds(280, y, 20, 20).build());
            StringWidget widget = new StringWidget(310, y, 120, 20, Component.literal("Condition " + i + "     OR"), font);

            String builder = (item.block == null ? "-\n" : item.block + " AND\n") +
                    (item.waterlogged == null ? "-\n" : "Waterlogged: " + item.waterlogged + " AND\n") +
                    (item.pistonBehavior == null ? "-\n" : "Piston behavior: " + item.pistonBehavior + " AND\n") +
                    (item.blastResistance == null ? "-" : "Blast resistance: " + item.blastResistance);
            widget.setTooltip(Tooltip.create(Component.literal(builder)));
            addRenderableWidget(widget);
            y += 23;
        }

        //presets
        addRenderableWidget(new StringWidget(width - 130, 10, 100, 20, Component.literal("Presets"), font));

        List<Whitelist> list = new ArrayList<>();
        Whitelist worldEater = new Whitelist(new ArrayList<>(){{
            add(new WhitelistItem(null, null, ">9", "≠DESTROY"));
        }}, "World eater");
        Whitelist fluidsAndWaterlogged = new Whitelist(new ArrayList<>(){{
            add(new WhitelistItem(Blocks.LAVA, null, null, null));
            add(new WhitelistItem(Blocks.WATER, null, null, null));
            add(new WhitelistItem(null, "true", null, null));
        }}, "Fluids and Waterlogged");
        Whitelist quarry = new Whitelist(new ArrayList<>(){{
            add(new WhitelistItem(null, null, null, "=IMMOVABLE"));
            add(new WhitelistItem(null, null, ">9", "≠DESTROY"));
        }}, "Quarry");

        list.add(worldEater);
        list.add(fluidsAndWaterlogged);
        list.add(quarry);
        ToggelableWidgedDropDownList<Whitelist> presets = new ToggelableWidgedDropDownList<>(width - 180, 30, 150, 20, 100, 5, list);
        addWidget(presets);
        addRenderableWidget(Button.builder(Component.literal("Use preset"), btn -> {
            if(presets.getSelectedEntry() != null){
                current_whitelist.addAll(presets.getSelectedEntry().whitelist);
                WhitelistManager.saveData(new Whitelist(current_whitelist), filename);
                minecraft.setScreen(new WhitelistEditorScreen(parent, filename, 0));
            }
        }).bounds(width - 110, 115, 80, 20).build());


        int buttonY = height - 40;
        if (currentPage > 0) {
            addRenderableWidget(Button.builder(Component.literal("< Prev"), btn ->
                    minecraft.setScreen(new WhitelistEditorScreen(parent, filename, currentPage - 1))
            ).bounds(width / 2 - 120, buttonY, 80, 20).build());
        }

        addRenderableWidget(Button.builder(
                Component.literal("Page " + (currentPage + 1) + "/" + Math.max(1, totalPages)),
                btn -> {}
        ).bounds(width / 2 - 40, buttonY, 80, 20).build());

        if (to < current_whitelist.size()) {
            addRenderableWidget(Button.builder(Component.literal("Next >"), btn ->
                    minecraft.setScreen(new WhitelistEditorScreen(parent, filename, currentPage + 1))
            ).bounds(width / 2 + 40, buttonY, 80, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> minecraft.setScreen(parent))
                .bounds(30, height - 30, 80, 20).build());

        addToWhitelistBtn = Button.builder(Component.literal("Add to whitelist"), btn -> {
                    if(validateCreatedWhitelistItem()){
                        current_whitelist.add(createdWhitelistItem);
                        WhitelistManager.saveData(new Whitelist(current_whitelist), filename);
                        minecraft.setScreen(new WhitelistEditorScreen(parent, filename, 0));
                    }
                }).bounds(30, 260, 90, 20).build();
        addRenderableWidget(addToWhitelistBtn);

    }

    private boolean validateCreatedWhitelistItem(){
        return createdWhitelistItem.block != null || createdWhitelistItem.waterlogged != null || createdWhitelistItem.pistonBehavior != null || createdWhitelistItem.blastResistance != null;
    }


    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        try {
            if(!blockInput.getValue().isEmpty() && BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(blockInput.getValue()))){
                createdWhitelistItem.block = BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(blockInput.getValue()));
            }else createdWhitelistItem.block = null;
        }catch (Exception ignored){}
        //waterlogged
        if(waterloggedWidget.getSelectedEntry() != null){
            createdWhitelistItem.waterlogged = waterloggedWidget.getSelectedEntry();
        }else createdWhitelistItem.waterlogged = null;

        //pistonBehavior
        if(equalsOperatorsWidget.getSelectedEntry() != null && pistonBehaviorWidget.getSelectedEntry() != null){
            createdWhitelistItem.pistonBehavior = equalsOperatorsWidget.getSelectedEntry() + pistonBehaviorWidget.getSelectedEntry();
        }else createdWhitelistItem.pistonBehavior = null;

        //blast resistance
        if(comparisonOperatorsWidget.getSelectedEntry() != null && !blastResistanceValue.getValue().isEmpty()){
            createdWhitelistItem.blastResistance = comparisonOperatorsWidget.getSelectedEntry() + blastResistanceValue.getValue();
        }else createdWhitelistItem.blastResistance = null;

        //borders
        context.submitOutline(20,20, 245, 265, CommonColors.LIGHT_GRAY);
        context.submitOutline(275,20, 130, height - 80, CommonColors.LIGHT_GRAY);

        addToWhitelistBtn.active = validateCreatedWhitelistItem();
        super.render(context, mouseX, mouseY, delta);
        context.drawString(font, Component.literal(createdWhitelistItem.toString()), 30,240, CommonColors.WHITE, true);
    }
}
