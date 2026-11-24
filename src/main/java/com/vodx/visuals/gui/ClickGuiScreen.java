package com.vodx.visuals.gui;

import com.vodx.visuals.Category;
import com.vodx.visuals.Colors;
import com.vodx.visuals.Module;
import com.vodx.visuals.Modules;
import com.vodx.visuals.Setting;
import com.vodx.visuals.VodxVisualsClient;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Pulse-style menu: category sidebar, search bar, scrollable module cards with expandable settings. */
public class ClickGuiScreen extends Screen {
    private static final int SB = 96, HD = 36, ROW_H = 30, SET_H = 22, GAP = 4;

    private record Row(Module m, int y, int h) { }

    private Category tab = null; // null = all
    private String query = "";
    private double scroll, targetScroll;
    private int contentH;
    private Setting.Num dragging;
    private int dragX, dragW;
    private int px, py, pw, ph, cx, cy, cw, ch;

    public ClickGuiScreen() {
        super(Text.literal("Vodx Visuals"));
    }

    @Override
    protected void init() {
        pw = Math.min(500, width - 20);
        ph = Math.min(310, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + SB + 8;
        cy = py + HD + 6;
        cw = pw - SB - 16;
        ch = ph - HD - 12;
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        boolean searching = !query.isBlank();
        int y = cy - (int) Math.round(scroll);
        int total = 0;
        for (Module m : Modules.ALL) {
            if (!searching && tab != null && m.category != tab) continue;
            if (searching && !m.matches(query)) continue;
            int h = ROW_H + (m.expanded ? m.settings.size() * SET_H + 6 : 0);
            out.add(new Row(m, y, h));
            y += h + GAP;
            total += h + GAP;
        }
        contentH = total;
        return out;
    }

    private int setY(Row r, int i) {
        return r.y() + ROW_H + 3 + i * SET_H;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        ctx.fill(px, py, px + pw, py + ph, Colors.PANEL);
        ctx.drawBorder(px, py, pw, ph, Colors.alpha(Colors.accent(0), 90));
        for (int i = 0; i < pw; i += 4) ctx.fill(px + i, py, Math.min(px + i + 4, px + pw), py + 2, Colors.accent(i));

        // sidebar
        ctx.fill(px, py + 2, px + SB, py + ph, Colors.SIDEBAR);
        ctx.drawText(textRenderer, "VODX", px + 10, py + 10, Colors.accent(0), true);
        ctx.drawText(textRenderer, "VISUALS", px + 10, py + 21, Colors.MUTED, false);
        int ty = py + HD + 8;
        drawTab(ctx, "All", null, ty, mx, my);
        ty += 24;
        for (Category c : Category.values()) {
            drawTab(ctx, c.label, c, ty, mx, my);
            ty += 24;
        }
        ctx.drawText(textRenderer, "v2.0.0", px + 10, py + ph - 14, Colors.MUTED, false);

        // search bar
        int sx = px + SB + 8, sy = py + 8, sw = pw - SB - 16;
        ctx.fill(sx, sy, sx + sw, sy + 20, Colors.FIELD);
        ctx.drawBorder(sx, sy, sw, 20, Colors.alpha(Colors.accent(0), 140));
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        if (query.isEmpty()) {
            ctx.drawText(textRenderer, "Search functions...", sx + 6, sy + 6, Colors.MUTED, false);
        } else {
            ctx.drawText(textRenderer, query + (caret ? "_" : ""), sx + 6, sy + 6, Colors.TEXT, false);
        }

        // content
        List<Row> rows = rows();
        targetScroll = MathHelper.clamp(targetScroll, 0, Math.max(0, contentH - ch));
        scroll += (targetScroll - scroll) * 0.35;

        if (!query.isEmpty()) {
            String cnt = rows.size() + " found";
            ctx.drawText(textRenderer, cnt, sx + sw - textRenderer.getWidth(cnt) - 6, sy + 6, Colors.MUTED, false);
        }

        ctx.enableScissor(cx, cy, cx + cw, cy + ch);
        if (rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No functions found", cx + cw / 2, cy + 20, Colors.MUTED);
        }
        for (Row r : rows) {
            if (r.y() + r.h() < cy || r.y() > cy + ch) continue;
            drawRow(ctx, r, mx, my);
        }
        ctx.disableScissor();
    }

    private void drawTab(DrawContext ctx, String label, Category c, int y, int mx, int my) {
        int x = px + 8, w = SB - 16;
        boolean active = query.isBlank() && tab == c;
        boolean hover = inside(mx, my, x, y, w, 20);
        if (active) {
            ctx.fill(x, y, x + w, y + 20, Colors.alpha(Colors.accent(y), 60));
            ctx.fill(x, y, x + 2, y + 20, Colors.accent(y));
        } else if (hover) {
            ctx.fill(x, y, x + w, y + 20, 0x20FFFFFF);
        }
        ctx.drawText(textRenderer, label, x + 10, y + 6, active ? Colors.TEXT : Colors.MUTED, true);
    }

    private void drawRow(DrawContext ctx, Row r, int mx, int my) {
        Module m = r.m();
        boolean hover = inside(mx, my, cx, r.y(), cw, ROW_H) && my >= cy && my < cy + ch;
        ctx.fill(cx, r.y(), cx + cw, r.y() + r.h(), hover ? Colors.CARD_HOVER : Colors.CARD);
        if (m.enabled) ctx.fill(cx, r.y(), cx + 2, r.y() + ROW_H, Colors.accent(r.y()));

        ctx.drawText(textRenderer, m.name, cx + 10, r.y() + 6, m.enabled ? Colors.TEXT : 0xFFB0B0C4, true);
        String desc = textRenderer.trimToWidth(m.description, cw - 90);
        ctx.drawText(textRenderer, desc, cx + 10, r.y() + 17, Colors.MUTED, false);

        int swW = 26, swH = 12;
        int sx = cx + cw - swW - 10, sy = r.y() + (ROW_H - swH) / 2;
        m.anim += ((m.enabled ? 1f : 0f) - m.anim) * 0.25f;
        ctx.fill(sx, sy, sx + swW, sy + swH, Colors.mix(0xFF343448, Colors.accent(r.y()), m.anim));
        int kx = sx + 2 + (int) ((swW - swH) * m.anim);
        ctx.fill(kx, sy + 2, kx + swH - 4, sy + swH - 2, 0xFFFFFFFF);
        if (!m.settings.isEmpty()) {
            ctx.drawText(textRenderer, m.expanded ? "-" : "+", sx - 14, r.y() + 11, Colors.MUTED, false);
        }

        if (!m.expanded) return;
        for (int i = 0; i < m.settings.size(); i++) {
            Setting s = m.settings.get(i);
            int y = setY(r, i);
            int x = cx + 18, w = cw - 36;
            ctx.drawText(textRenderer, s.name, x, y + 3, Colors.TEXT, false);
            if (s instanceof Setting.Bool b) {
                int bx = x + w - 12;
                ctx.fill(bx, y + 2, bx + 12, y + 14, 0xFF343448);
                if (b.value) ctx.fill(bx + 2, y + 4, bx + 10, y + 12, Colors.accent(y));
            } else if (s instanceof Setting.Mode md) {
                String v = md.get();
                ctx.drawText(textRenderer, v, x + w - textRenderer.getWidth(v), y + 3, Colors.accent(y), false);
            } else if (s instanceof Setting.Num n) {
                String v = n.display();
                ctx.drawText(textRenderer, v, x + w - textRenderer.getWidth(v), y + 3, Colors.MUTED, false);
                ctx.fill(x, y + 15, x + w, y + 18, 0xFF343448);
                double t = (n.value - n.min) / (n.max - n.min);
                int fw = (int) (w * t);
                ctx.fill(x, y + 15, x + fw, y + 18, Colors.accent(y));
                ctx.fill(x + fw - 1, y + 13, x + fw + 2, y + 20, 0xFFFFFFFF);
            }
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // tabs
        int ty = py + HD + 8;
        if (mx >= px + 8 && mx < px + SB - 8) {
            if (inside(mx, my, px + 8, ty, SB - 16, 20)) { selectTab(null); return true; }
            ty += 24;
            for (Category c : Category.values()) {
                if (inside(mx, my, px + 8, ty, SB - 16, 20)) { selectTab(c); return true; }
                ty += 24;
            }
        }
        // clear search
        if (!query.isEmpty() && inside(mx, my, px + SB + 8, py + 8, pw - SB - 16, 20) && button == 1) {
            query = "";
            targetScroll = 0;
            return true;
        }
        if (!inside(mx, my, cx, cy, cw, ch)) return super.mouseClicked(mx, my, button);

        for (Row r : rows()) {
            Module m = r.m();
            if (inside(mx, my, cx, r.y(), cw, ROW_H)) {
                int sx = cx + cw - 26 - 10;
                boolean arrowZone = !m.settings.isEmpty() && mx >= sx - 24 && mx < sx - 2;
                if (button == 1 || (button == 0 && arrowZone)) {
                    if (!m.settings.isEmpty()) m.expanded = !m.expanded;
                } else if (button == 0) {
                    m.toggle();
                }
                return true;
            }
            if (m.expanded) {
                for (int i = 0; i < m.settings.size(); i++) {
                    int y = setY(r, i);
                    if (!inside(mx, my, cx + 10, y, cw - 20, SET_H)) continue;
                    Setting s = m.settings.get(i);
                    if (s instanceof Setting.Bool b) {
                        b.value = !b.value;
                    } else if (s instanceof Setting.Mode md) {
                        if (button == 1) md.prev(); else md.next();
                    } else if (s instanceof Setting.Num n && button == 0) {
                        dragging = n;
                        dragX = cx + 18;
                        dragW = cw - 36;
                        updateSlider(mx);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void selectTab(Category c) {
        tab = c;
        query = "";
        scroll = 0;
        targetScroll = 0;
    }

    private void updateSlider(double mx) {
        if (dragging == null) return;
        double t = MathHelper.clamp((mx - dragX) / dragW, 0, 1);
        dragging.set(dragging.min + t * (dragging.max - dragging.min));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            updateSlider(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        targetScroll = MathHelper.clamp(targetScroll - vertical * 30, 0, Math.max(0, contentH - ch));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            targetScroll = 0;
            return true;
        }
        if (keyCode >= 256 && VodxVisualsClient.MENU_KEY != null
                && VodxVisualsClient.MENU_KEY.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr >= 32 && chr != 127 && query.length() < 24) {
            query += chr;
            targetScroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Modules.save();
        super.removed();
    }
}
