package ru.obabok.client.gui.widgets;

import fi.dy.masa.malilib.gui.widgets.WidgetDropDownList;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.List;


public class ToggelableWidgedDropDownList<T> extends WidgetDropDownList<T> {
    public boolean active = true;

    public ToggelableWidgedDropDownList(int x, int y, int width, int height, int maxHeight, int maxVisibleEntries, List<T> entries) {
        super(x, y, width, height, maxHeight, maxVisibleEntries, entries);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if(!active) return false;
        return super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if(!active) return false;
        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onMouseReleased(MouseButtonEvent click) {
        if(!active) return;
        super.onMouseReleased(click);
    }

//    @Override
//    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
//        Matrix4fStack matrixStack = RenderSystem.getModelViewStack();
//        matrixStack.pushMatrix();
//        matrixStack.translate(0.0F, 0.0F, 10.0F);
//        Matrix3x2fStack matrixStackIn = ctx.pose();
//        matrixStackIn.pushMatrix();
//        matrixStackIn.translate(0.0F, 0.0F);
//        List<T> list = this.filteredEntries;
//        int visibleEntries = Math.min(this.maxVisibleEntries, list.size());
//        RenderSystem.depthMask(true);
//        RenderSystem.enableDepthTest();
//        RenderUtils.drawOutlinedBox(ctx, this.x + 1, this.y, this.width - 2, this.height - 1, -15724528, -4144960);
//        String str = this.getDisplayString(this.getSelectedEntry());
//        int txtX = this.x + 4;
//        int txtY = this.y + this.height / 2 - this.fontHeight / 2;
//        matrixStackIn.translate(0.0F, 0.0F, 100.0F);
//        this.drawString(txtX, txtY, -2039584, str, drawContext);
//        txtY += this.height + 1;
//        int scrollWidth = 10;
//        if (this.isOpen) {
//            if (!this.searchBar.getTextField().getText().isEmpty()) {
//                this.searchBar.draw(mouseX, mouseY, drawContext);
//            }
//
//            RenderSystem.depthMask(true);
//            RenderSystem.enableDepthTest();
//            RenderUtils.drawOutline(this.x, this.y + this.height, this.width, visibleEntries * this.height + 2, -2039584);
//            int y = this.y + this.height + 1;
//            int startIndex = Math.max(0, this.scrollBar.getValue());
//            int max = Math.min(startIndex + this.maxVisibleEntries, list.size());
//
//            for(int i = startIndex; i < max; ++i) {
//                int bg = (i & 1) != 0 ? Colors.BLACK : Colors.GRAY;
//                if (mouseX >= this.x && mouseX < this.x + this.width - scrollWidth && mouseY >= y && mouseY < y + this.height) {
//                    bg = Colors.LIGHT_GRAY;
//                }
//
//                RenderSystem.depthMask(true);
//                RenderSystem.enableDepthTest();
//                RenderUtils.drawRect(this.x, y, this.width - scrollWidth, this.height, bg);
//                str = this.getDisplayString(list.get(i));
//                this.drawString(txtX, txtY, -2039584, str, drawContext);
//                y += this.height;
//                txtY += this.height;
//            }
//
//            int x = this.x + this.width - this.scrollbarWidth - 1;
//            y = this.y + this.height + 1;
//            int h = visibleEntries * this.height;
//            int totalHeight = Math.max(h, list.size() * this.height);
//            RenderSystem.depthMask(true);
//            RenderSystem.enableDepthTest();
//            this.scrollBar.render(mouseX, mouseY, 0.0F, x, y, this.scrollbarWidth, h, totalHeight, drawContext);
//            this.bindTexture(MaLiLibIcons.TEXTURE);
//            MaLiLibIcons i = MaLiLibIcons.ARROW_UP;
//            RenderUtils.drawTexturedRect(this.x + this.width - 16, this.y + 2, i.getU() + i.getWidth(), i.getV(), i.getWidth(), i.getHeight());
//        } else {
//            this.bindTexture(MaLiLibIcons.TEXTURE);
//            MaLiLibIcons i = MaLiLibIcons.ARROW_DOWN;
//            RenderUtils.drawTexturedRect(this.x + this.width - 16, this.y + 2, i.getU() + i.getWidth(), i.getV(), i.getWidth(), i.getHeight());
//        }
//
//        matrixStack.popMatrix();
//        matrixStackIn.pop();
//    }

}
