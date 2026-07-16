package ru.obabok.client.gui.screens;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class NoMalilibScreen extends Screen {

    public NoMalilibScreen() {
        super(Component.literal("No Malilib"));
    }

    @Override
    protected void init() {
        super.init();
        Component noMalilibText = Component.literal("Malilib mod not found, unable to load AreaScanner");
        addRenderableWidget(new StringWidget(width / 2 - font.width(noMalilibText) / 2, height / 2, font.width(noMalilibText), 20, noMalilibText, font));
        addRenderableWidget(Button.builder(Component.literal("Exit"), btn -> System.exit(0)).bounds(width / 2 - 40, height / 2 + 40, 80, 20).build());
    }
}
