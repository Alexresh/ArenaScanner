package ru.obabok.client.gui.screens;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sounds.SoundEvents;
import ru.obabok.client.Config;
import ru.obabok.client.network.ClientNetwork;
import ru.obabok.common.References;

import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ConfigGui extends GuiConfigsBase {
    private static ConfigGuiTab tab = ConfigGuiTab.GENERIC;
    public ConfigGui() {
        super(10, 50, References.MOD_ID, null, "areascanner.gui.title.configs");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.clearOptions();
        int x = 10;
        int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values())
        {
            x += this.createButton(x, y, -1, tab) + 2;
        }
        ButtonGeneric testBtn = new ButtonGeneric(width - 75, getScreenHeight() - 30, 65, 20, "Test");
        this.addButton(testBtn, (btn, mousebtn)->{
            minecraft.player.playSound(SoundEvents.AMBIENT_CAVE.value());
            onClose();
            //minecraft.setScreenAndShow(new AlertScreen(() -> {onClose();}, Component.literal("Ты кому звониш?"), Component.literal("а?"), Component.literal("Тебе"), true));
            //minecraft.setScreenAndShow(new TitleScreen(true, null));
            //onClose();
//            if(Scan.getRange() != null){
//                AsyncObsidianScanner.startAsyncScan(Minecraft.getInstance().level, Scan.getRange().aabb());
//            }
        });


        ButtonGeneric whitelistsButton = new ButtonGeneric(10, getScreenHeight() - 30, 65, 20, "Whitelists");
        this.addButton(whitelistsButton, (button1, mouseButton) -> openGui(new WhitelistSelectorScreen(this, 0)));
        ButtonGeneric taskButton = new ButtonGeneric(85, getScreenHeight() - 30, 40, 20, "Task");
        this.addButton(taskButton, (button1, mouseButton) -> openGui(new ScanTaskScreen(this)));
        ButtonGeneric sharedButton = new ButtonGeneric(135, getScreenHeight() - 30, 60, 20, "Shared");
        this.addButton(sharedButton, (button1, mouseButton) -> ClientNetwork.openSharedScansScreen(this, 0));
    }
    private int createButton(int x, int y, int width, ConfigGuiTab tab)
    {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(ConfigGui.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth();
    }

    private record ButtonListener(ConfigGuiTab tab, ConfigGui parent) implements IButtonActionListener {

        @Override
            public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
                ConfigGui.tab = this.tab;

                this.parent.reCreateListWidget();
                if (parent.getListWidget() != null)
                    this.parent.getListWidget().resetScrollbarPosition();
                this.parent.initGui();
            }
        }


    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<? extends IConfigBase> configs;
        ConfigGuiTab tab = ConfigGui.tab;
        if (tab == ConfigGuiTab.GENERIC){
            configs = Config.Generic.OPTIONS;
        }else if (tab == ConfigGuiTab.HUD)
        {
            configs = Config.Hud.OPTIONS;
        }
        else
        {
            return Collections.emptyList();
        }

        return ConfigOptionWrapper.createFor(configs);
    }


    public enum ConfigGuiTab
    {
        GENERIC ("areascanner.gui.title.generic"),
        HUD ("areascanner.gui.title.hud");

        private final String translationKey;

        ConfigGuiTab(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }
}
