package ru.obabok.client.models;

import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ScreenPlus extends Screen {
    protected final List<WidgetBase> widgets = new ArrayList<>();
    protected WidgetBase hoveredWidget = null;
    protected ScreenPlus(Component title) {
        super(title);
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.drawWidgets(mouseX, mouseY, context);
        this.drawHoveredWidget(mouseX, mouseY, context);
    }


    public boolean onMouseClicked(MouseButtonEvent event)
    {
        boolean handled = false;
        for (WidgetBase widget : this.widgets)
        {
            if (widget.isMouseOver((int) event.x(), (int)event.y()) && widget.onMouseClicked(event, false))
            {
                handled = true;
            }
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        for (WidgetBase widget : this.widgets)
        {
            widget.onMouseReleased(mouseButtonEvent);
        }
        return super.mouseReleased(mouseButtonEvent);
    }

    protected void drawWidgets(int mouseX, int mouseY, GuiGraphicsExtractor drawContext)
    {
        this.hoveredWidget = null;

        if (!this.widgets.isEmpty())
        {
            for (WidgetBase widget : this.widgets)
            {
                widget.render(GuiContext.fromGuiGraphics(drawContext), mouseX, mouseY, false);

                if (widget.isMouseOver(mouseX, mouseY))
                {
                    this.hoveredWidget = widget;
                }
            }
        }
    }

    protected boolean shouldRenderHoverStuff()
    {
        return minecraft.gui.screen() == this;
    }


    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean bl) {
        onMouseClicked(mouseButtonEvent);
        return super.mouseClicked(mouseButtonEvent, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (WidgetBase widget : this.widgets)
        {
            if (widget.onMouseScrolled((int)mouseX, (int)mouseY, horizontalAmount, verticalAmount))
            {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected void drawHoveredWidget(int mouseX, int mouseY, GuiGraphicsExtractor drawContext)
    {
        if (!this.shouldRenderHoverStuff())
        {
            return;
        }

        if (this.hoveredWidget != null)
        {
            this.hoveredWidget.postRenderHovered(GuiContext.fromGuiGraphics(drawContext), mouseX, mouseY, false);
        }
    }

    public <T extends WidgetBase> T addWidget(T widget)
    {
        this.widgets.add(widget);
        return widget;
    }

}
