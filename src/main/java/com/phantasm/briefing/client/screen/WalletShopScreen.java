package com.phantasm.briefing.client.screen;

import com.phantasm.briefing.client.widgets.SolidColorRectWidget;
import com.phantasm.briefing.data.WalletShopOfferEntry;
import com.phantasm.briefing.network.ModNetwork;
import com.phantasm.briefing.network.packet.CloseWalletShopC2SPacket;
import com.phantasm.briefing.network.packet.PurchaseWalletShopOfferC2SPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class WalletShopScreen extends PixelGridScreen {
    private static final int BASE_PANEL_WIDTH = 736;
    private static final int BASE_PANEL_HEIGHT = 332;
    private static final int MIN_PANEL_WIDTH = 420;
    private static final int MIN_PANEL_HEIGHT = 236;
    private static final int BASE_SIDEBAR_WIDTH = 156;
    private static final float ITEM_ID_SCALE = 0.78F;

    private final String shopId;
    private final String shopTitle;
    private final String shopSubtitle;
    private final String balanceLabel;
    private final String balanceIconItemId;
    private final List<WalletShopOfferEntry> offers;
    private final List<CategoryTab> categories;
    private String selectedCategoryId;
    private String selectedEntryId;
    private int scrollOffset;

    public WalletShopScreen(
            String shopId,
            String shopTitle,
            String shopSubtitle,
            String balanceLabel,
            String balanceIconItemId,
            List<WalletShopOfferEntry> offers
    ) {
        super(Component.literal(Objects.requireNonNull(shopTitle)));
        this.shopId = shopId;
        this.shopTitle = shopTitle;
        this.shopSubtitle = shopSubtitle;
        this.balanceLabel = balanceLabel;
        this.balanceIconItemId = balanceIconItemId;
        this.offers = deduplicateOffers(offers);
        this.categories = buildCategories(this.offers);
        this.selectedCategoryId = "";
        this.selectedEntryId = this.offers.isEmpty() ? "" : this.offers.get(0).entryId();
        this.scrollOffset = 0;
    }

    @Override
    protected void buildWidgets() {
        this.addPixelWidget(new SolidColorRectWidget(0, 0, 0, 0, 0x0));
    }

    @Override
    protected void renderModalBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x5C000000);
    }

    @Override
    protected void renderScreenContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Font font = Objects.requireNonNull(this.font);
        Layout layout = this.layout();
        int sidebarX = layout.panelX() + 20;
        int sidebarY = layout.panelY() + 62;
        int listBaseX = layout.panelX() + layout.contentX();
        int listX = listBaseX + layout.listInsetLeft();
        int listY = layout.panelY() + layout.listTop();
        int listWidth = layout.gridWidth();
        int detailX = listBaseX + layout.listWidth() + layout.contentGap();

        renderChronicleBackdrop(guiGraphics, layout);
        renderFrameChrome(guiGraphics, layout);
        guiGraphics.drawString(font, font.plainSubstrByWidth(this.shopTitle, layout.panelWidth() - 40), layout.panelX() + 16, layout.panelY() + 16, 0xA9D9FF, false);
        if (!layout.compact()) {
            String terminalLabel = font.plainSubstrByWidth(this.shopSubtitle.isBlank() ? "物资交易终端" : this.shopSubtitle, 160);
            int terminalX = layout.panelX() + layout.panelWidth() - 20 - font.width(terminalLabel);
            guiGraphics.drawString(font, terminalLabel, terminalX, layout.panelY() + 16, 0x7A9BC1, false);
        }
        guiGraphics.fill(layout.panelX() + 12, layout.panelY() + 38, layout.panelX() + layout.panelWidth() - 12, layout.panelY() + 39, 0x7A4D7CA9);
        guiGraphics.fill(layout.panelX() + layout.contentX() - 16, layout.panelY() + 52, layout.panelX() + layout.contentX() - 15, layout.panelY() + layout.panelHeight() - 46, 0x805A8FC2);
        renderHeaderBadge(guiGraphics, font, layout);

        this.renderSidebar(guiGraphics, font, mouseX, mouseY, sidebarX, sidebarY);
        this.renderRows(guiGraphics, font, mouseX, mouseY, listX, listY, listWidth, layout);
        this.renderScrollBar(guiGraphics, layout, listBaseX + layout.listWidth() - 6, listY);
        if (layout.showDetailPanel()) {
            this.renderDetailPanel(guiGraphics, font, layout, detailX, listY, layout.detailWidth(), layout.contentHeight());
        } else {
            this.renderSelectedSummary(guiGraphics, font, layout, listBaseX, layout.summaryY(), layout.listWidth() - 2);
        }
        this.renderFooter(guiGraphics, font, layout);
    }

    private void renderSidebar(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, int sidebarX, int sidebarY) {
        Layout layout = this.layout();
        int sidebarWidth = layout.sidebarWidth();
        int sidebarHeight = layout.panelHeight() - layout.listTop() - layout.footerHeight();
        int y = sidebarY;
        for (CategoryTab category : this.categories) {
            if (y + 28 > sidebarY + sidebarHeight - 4) {
                break;
            }
            boolean selected = category.categoryId().equals(this.selectedCategoryId);
            boolean hovered = isIn(mouseX, mouseY, sidebarX, y, sidebarWidth, 30);
            guiGraphics.fill(sidebarX, y, sidebarX + sidebarWidth, y + 30, selected ? 0xC91A2940 : hovered ? 0xAA152334 : 0x900B111A);
            if (selected) {
                guiGraphics.fill(sidebarX, y, sidebarX + 4, y + 30, 0x8FC8FF);
                guiGraphics.fill(sidebarX + 4, y, sidebarX + sidebarWidth, y + 1, 0x55426A94);
            }
            guiGraphics.drawString(font, font.plainSubstrByWidth(category.title(), Math.max(30, sidebarWidth - 20)), sidebarX + 14, y + 11, selected ? 0xDDF1FF : 0xD4E6F8, false);
            y += 38;
        }
    }

    private void renderRows(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, int listX, int listY, int listWidth, Layout layout) {
        List<WalletShopOfferEntry> visibleEntries = currentVisibleEntries();
        for (int visibleIndex = 0; visibleIndex < layout.visibleItemCount(); visibleIndex++) {
            int column = visibleIndex % layout.columnCount();
            int row = visibleIndex / layout.columnCount();
            int x = listX + column * (layout.cardWidth() + layout.columnGap());
            int y = listY + row * (layout.rowHeight() + layout.rowGap());
            int cardWidth = layout.cardWidth();
            if (visibleIndex >= visibleEntries.size()) {
                this.renderEmptyCard(guiGraphics, font, x, y, cardWidth, layout.rowHeight());
                continue;
            }

            WalletShopOfferEntry entry = visibleEntries.get(visibleIndex);
            boolean selected = entry.entryId().equals(this.selectedEntryId);
            boolean hovered = isIn(mouseX, mouseY, x, y, cardWidth, layout.rowHeight());
            int background = selected ? 0xB0101B2A : hovered ? 0x9A0D1521 : 0x86080D15;
            int titleColor = 0xE8E8E8;
            String displayTitle = displayTitleWithCount(entry);
            String itemId = entry.resultItemId();
            boolean compactCard = layout.rowHeight() <= 52;
            int iconX = x + 10;
            int iconY = y + (compactCard ? 6 : 10);
            int titleX = x + (compactCard ? 36 : 42);
            int titleY = y + (compactCard ? 6 : 10);
            int itemIdY = y + (compactCard ? 18 : 24);
            int titleWidth = Math.max(42, cardWidth - (titleX - x) - 12);
            int itemIdWidth = titleWidth;
            int accentColor = statusAccent(entry);

            guiGraphics.fill(x, y, x + cardWidth, y + layout.rowHeight(), background);
            guiGraphics.fill(x, y, x + 3, y + layout.rowHeight(), selected ? 0x8FC8FF : 0x4D7CA9);
            guiGraphics.fill(x + cardWidth - 2, y + 8, x + cardWidth - 1, y + layout.rowHeight() - 8, accentColor);
            guiGraphics.fill(x + 6, y, x + cardWidth, y + 1, 0x24384C);
            if (selected) {
                int pulseGlow = pulseColor(0x2A8FC8FF, 0x5E8FC8FF);
                guiGraphics.fill(x + 3, y + 1, x + cardWidth - 1, y + layout.rowHeight() - 1, 0x12233E5A);
                guiGraphics.fill(x + 4, y + 2, x + cardWidth - 4, y + layout.rowHeight() - 4, pulseGlow);
                drawCardCorners(guiGraphics, x, y, cardWidth, layout.rowHeight(), true);
                guiGraphics.fill(x + 10, y + layout.rowHeight() - 3, x + cardWidth - 10, y + layout.rowHeight() - 2, 0x708FC8FF);
            } else if (hovered) {
                guiGraphics.fill(x + 8, y + 1, x + cardWidth - 8, y + 2, 0x304D7CA9);
            }

            renderChronicleRewardIcons(guiGraphics, entry, iconX, iconY);
            guiGraphics.drawString(font, font.plainSubstrByWidth(displayTitle, titleWidth), titleX, titleY, titleColor, false);
            drawScaledString(guiGraphics, font, font.plainSubstrByWidth(itemId, Math.max(18, Math.round(itemIdWidth / 0.62F))), titleX, itemIdY, 0x8FA7C8, 0.62F);
        }

        if (visibleEntries.isEmpty()) {
            guiGraphics.drawString(font, "当前分类下没有可见商品。", listX + 12, listY + layout.listHeight() - 12, 0x8FA7C8, false);
        }
    }

    private void renderFooter(GuiGraphics guiGraphics, Font font, Layout layout) {
        int footerY = layout.panelY() + layout.panelHeight() - layout.footerHeight() - 6;
        WalletShopOfferEntry selected = selectedOffer();
        String controlsText = selected != null && !selected.walletPayment() ? "Enter 交换   Esc 关闭" : "Enter 购买   Esc 关闭";
        int controlsX = layout.panelX() + layout.panelWidth() - 144;
        guiGraphics.fill(layout.panelX() + 14, footerY - 4, layout.panelX() + layout.panelWidth() - 14, footerY - 3, 0x24384C);
        this.renderHoldingsPanel(guiGraphics, font, layout, selected, footerY, controlsX - 18);
        if (selected != null) {
            String selectionText = "已选 " + (indexOfSelected(filteredOffers()) + 1) + "/" + filteredOffers().size();
            int selectionX = layout.panelX() + 78;
            int selectionY = footerY - 16;
            guiGraphics.fill(selectionX - 6, selectionY - 2, selectionX + font.width(selectionText) + 8, selectionY + 10, 0x22162234);
            guiGraphics.drawString(font, selectionText, selectionX, selectionY, 0x84B0D9, false);
        }
        if (filteredOffers().size() > layout.rowsPerPage()) {
            guiGraphics.drawString(font, "滚轮滑动", controlsX - 68, footerY + 10, 0x7FA8CF, false);
        }
        guiGraphics.drawString(font, controlsText, controlsX, footerY + 10, 0x93BDE3, false);
    }

    private void renderHoldingsPanel(GuiGraphics guiGraphics, Font font, Layout layout, WalletShopOfferEntry selected, int footerY, int footerRightLimit) {
        int panelX = layout.panelX() + 18;
        int panelY = footerY - 1;
        int panelWidth = Math.min(220, Math.max(140, footerRightLimit - panelX - 18));
        int panelHeight = 28;
        guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x24131E2D);
        guiGraphics.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0x6A8FC8FF);
        guiGraphics.drawString(font, "持有", panelX + 10, panelY + 8, 0x9FCBF3, false);
        if (selected == null) {
            guiGraphics.drawString(font, "未选择商品", panelX + 40, panelY + 8, 0x7E9BBB, false);
            return;
        }
        if (selected.walletPayment()) {
            renderWalletHolding(guiGraphics, font, panelX + 40, panelY + 4, panelWidth - 48);
            return;
        }
        renderTradeHolding(guiGraphics, font, selected, panelX + 40, panelY + 4, panelWidth - 48);
    }

    private void renderWalletHolding(GuiGraphics guiGraphics, Font font, int x, int y, int width) {
        ItemStack balanceStack = resolveItemStack(this.balanceIconItemId, 1);
        if (!balanceStack.isEmpty()) {
            guiGraphics.renderItem(balanceStack, x, y);
        }
        String amountText = extractBalanceAmount();
        String currencyText = extractBalanceLabel();
        int textX = x + (balanceStack.isEmpty() ? 0 : 20);
        String title = amountText.isBlank() ? currencyText : currencyText + " " + amountText;
        guiGraphics.drawString(font, font.plainSubstrByWidth(title, Math.max(40, width - (textX - x))), textX, y + 2, 0xD7ECFF, false);
    }

    private void renderTradeHolding(GuiGraphics guiGraphics, Font font, WalletShopOfferEntry selected, int x, int y, int width) {
        int lineWidth = Math.max(40, width);
        int lineY = y;
        lineY = renderTradeHoldingLine(guiGraphics, font, selected.primaryCostItemId(), selected.primaryCostCount(), x, lineY, lineWidth);
        if (!selected.secondaryCostItemId().isBlank() && selected.secondaryCostCount() > 0) {
            renderTradeHoldingLine(guiGraphics, font, selected.secondaryCostItemId(), selected.secondaryCostCount(), x, lineY, lineWidth);
        }
    }

    private int renderTradeHoldingLine(GuiGraphics guiGraphics, Font font, String itemId, int needCount, int x, int y, int width) {
        ItemStack stack = resolveItemStack(itemId, Math.max(1, needCount));
        if (!stack.isEmpty()) {
            guiGraphics.renderItem(stack, x, y);
        }
        int heldCount = countPlayerItems(itemId);
        int textX = x + 20;
        String itemName = stack.isEmpty() ? itemId : stack.getHoverName().getString();
        String text = itemName + " " + heldCount + "/" + needCount;
        guiGraphics.drawString(font, font.plainSubstrByWidth(text, Math.max(24, width - 20)), textX, y + 2, heldCount >= needCount ? 0xD7ECFF : 0xAFC4DA, false);
        return y + 12;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }

        Layout layout = this.layout();
        if (this.handleCategoryClick(mouseX, mouseY, layout.panelX(), layout.panelY())) {
            return true;
        }
        if (this.handleRowClick(mouseX, mouseY, layout.panelX(), layout.panelY())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxOffset = maxScrollOffset();
        if (maxOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        if (delta < 0.0D) {
            this.scrollOffset = Math.min(maxOffset, this.scrollOffset + 1);
            return true;
        }
        if (delta > 0.0D) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        ModNetwork.CHANNEL.sendToServer(new CloseWalletShopC2SPacket(this.shopId));
        super.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            this.moveSelection(-this.layout().columnCount());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            this.moveSelection(this.layout().columnCount());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            this.moveSelection(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            this.moveSelection(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.purchaseSelectedOffer();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleCategoryClick(double mouseX, double mouseY, int panelX, int panelY) {
        Layout layout = this.layout();
        int x = panelX + 20;
        int y = panelY + 62;
        for (CategoryTab category : this.categories) {
            if (isIn(mouseX, mouseY, x, y, layout.sidebarWidth(), 30)) {
                this.selectedCategoryId = category.categoryId();
                this.scrollOffset = 0;
                this.ensureSelectionVisible();
                return true;
            }
            y += 38;
        }
        return false;
    }

    private boolean handleRowClick(double mouseX, double mouseY, int panelX, int panelY) {
        Layout layout = this.layout();
        int listX = panelX + layout.contentX();
        int listY = panelY + layout.listTop();
        List<WalletShopOfferEntry> visibleEntries = currentVisibleEntries();
        for (int visibleIndex = 0; visibleIndex < visibleEntries.size(); visibleIndex++) {
            WalletShopOfferEntry entry = visibleEntries.get(visibleIndex);
            int column = visibleIndex % layout.columnCount();
            int row = visibleIndex / layout.columnCount();
            int x = listX + layout.listInsetLeft() + column * (layout.cardWidth() + layout.columnGap());
            int y = listY + row * (layout.rowHeight() + layout.rowGap());
            if (isIn(mouseX, mouseY, x, y, layout.cardWidth(), layout.rowHeight())) {
                this.selectedEntryId = entry.entryId();
                return true;
            }
        }
        return false;
    }

    private void moveSelection(int delta) {
        List<WalletShopOfferEntry> filtered = filteredOffers();
        if (filtered.isEmpty()) {
            return;
        }
        int currentIndex = indexOfSelected(filtered);
        int nextIndex = Math.max(0, Math.min(filtered.size() - 1, currentIndex + delta));
        this.selectedEntryId = filtered.get(nextIndex).entryId();
        this.ensureSelectionVisible();
    }

    private void purchaseSelectedOffer() {
        WalletShopOfferEntry entry = selectedOffer();
        if (entry != null) {
            this.purchaseEntry(entry);
        }
    }

    private void purchaseEntry(WalletShopOfferEntry entry) {
        if (!entry.unlocked() || !entry.affordable()) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new PurchaseWalletShopOfferC2SPacket(this.shopId, entry.entryId()));
    }

    private void ensureSelectionVisible() {
        List<WalletShopOfferEntry> filtered = filteredOffers();
        if (filtered.isEmpty()) {
            this.selectedEntryId = "";
            this.scrollOffset = 0;
            return;
        }
        if (filtered.stream().noneMatch(entry -> entry.entryId().equals(this.selectedEntryId))) {
            this.selectedEntryId = filtered.get(0).entryId();
        }
        int selectedIndex = indexOfSelected(filtered);
        Layout layout = this.layout();
        int selectedRow = selectedIndex / layout.columnCount();
        int visibleRows = layout.rowsPerPage();
        int maxOffset = maxScrollOffset();
        if (selectedRow < this.scrollOffset) {
            this.scrollOffset = selectedRow;
        } else if (selectedRow >= this.scrollOffset + visibleRows) {
            this.scrollOffset = selectedRow - visibleRows + 1;
        }
        this.scrollOffset = Math.max(0, Math.min(maxOffset, this.scrollOffset));
    }

    private WalletShopOfferEntry selectedOffer() {
        return filteredOffers().stream()
                .filter(entry -> entry.entryId().equals(this.selectedEntryId))
                .findFirst()
                .orElse(null);
    }

    private List<WalletShopOfferEntry> currentVisibleEntries() {
        List<WalletShopOfferEntry> filtered = filteredOffers();
        Layout layout = this.layout();
        int fromIndex = Math.min(filtered.size(), this.scrollOffset * layout.columnCount());
        int toIndex = Math.min(filtered.size(), fromIndex + layout.visibleItemCount());
        return filtered.subList(fromIndex, toIndex);
    }

    private List<WalletShopOfferEntry> filteredOffers() {
        if (this.selectedCategoryId.isBlank()) {
            return this.offers;
        }
        return this.offers.stream()
                .filter(entry -> this.selectedCategoryId.equals(entry.categoryId()))
                .toList();
    }

    private int maxScrollOffset() {
        Layout layout = this.layout();
        int totalRows = (int) Math.ceil(filteredOffers().size() / (double) layout.columnCount());
        return Math.max(0, totalRows - layout.rowsPerPage());
    }

    private int indexOfSelected(List<WalletShopOfferEntry> filtered) {
        for (int i = 0; i < filtered.size(); i++) {
            if (filtered.get(i).entryId().equals(this.selectedEntryId)) {
                return i;
            }
        }
        return 0;
    }

    private void renderResultIcon(GuiGraphics guiGraphics, WalletShopOfferEntry entry, int x, int y) {
        ItemStack stack = resolveItemStack(entry.resultItemId(), Math.max(1, entry.resultCount()));
        if (stack.isEmpty()) {
            guiGraphics.fill(x, y, x + 18, y + 18, 0x663C4A5D);
            return;
        }
        guiGraphics.renderItem(stack, x, y);
    }

    private void renderChronicleBackdrop(GuiGraphics guiGraphics, Layout layout) {
        int left = layout.panelX();
        int top = layout.panelY();
        int right = left + layout.panelWidth();
        int bottom = top + layout.panelHeight();
        guiGraphics.fill(left, top, right, bottom, 0x90060B12);
        guiGraphics.fillGradient(left, top, right, bottom, 0x1E143454, 0x08040910);
    }

    private void renderHeaderBadge(GuiGraphics guiGraphics, Font font, Layout layout) {
        int visibleCount = filteredOffers().size();
        WalletShopOfferEntry selected = selectedOffer();
        String badgeText = visibleCount + " 件商品";
        String modeText = selected != null && !selected.walletPayment() ? "以物易物" : "钱包结算";
        int modeWidth = font.width(modeText) + 12;
        int badgeWidth = font.width(badgeText) + 12;
        int badgeRight = layout.panelX() + layout.panelWidth() - 18;
        int badgeLeft = badgeRight - badgeWidth;
        int badgeTop = layout.panelY() + 44;
        guiGraphics.fill(badgeLeft, badgeTop, badgeRight, badgeTop + 14, 0x3A1B3047);
        guiGraphics.fill(badgeLeft, badgeTop, badgeLeft + 2, badgeTop + 14, 0x8FC8FF);
        guiGraphics.drawString(font, badgeText, badgeLeft + 6, badgeTop + 4, 0xCFE7FF, false);
        int modeRight = badgeLeft - 6;
        int modeLeft = modeRight - modeWidth;
        guiGraphics.fill(modeLeft, badgeTop, modeRight, badgeTop + 14, 0x2D1B273A);
        guiGraphics.fill(modeLeft, badgeTop, modeLeft + 2, badgeTop + 14, 0xC79B5A);
        guiGraphics.drawString(font, modeText, modeLeft + 6, badgeTop + 4, 0xF0D8A6, false);
    }

    private void renderScrollBar(GuiGraphics guiGraphics, Layout layout, int x, int y) {
        List<WalletShopOfferEntry> filtered = filteredOffers();
        int visibleRows = layout.rowsPerPage();
        int trackHeight = layout.listHeight();
        guiGraphics.fill(x, y, x + 4, y + trackHeight, 0x241C2D40);
        int totalRows = (int) Math.ceil(filtered.size() / (double) layout.columnCount());
        if (totalRows <= visibleRows) {
            guiGraphics.fill(x, y, x + 4, y + trackHeight, 0x4A406A98);
            return;
        }
        int thumbHeight = Math.max(18, Math.round(trackHeight * (visibleRows / (float) totalRows)));
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbOffset = Math.round((this.scrollOffset / (float) maxScrollOffset()) * travel);
        guiGraphics.fill(x, y + thumbOffset, x + 4, y + thumbOffset + thumbHeight, 0xAA8FC8FF);
        guiGraphics.fill(x, y + thumbOffset, x + 4, y + thumbOffset + 2, 0xD7ECFF);
    }

    private void renderChronicleRewardIcons(GuiGraphics guiGraphics, WalletShopOfferEntry entry, int x, int y) {
        ItemStack main = resolveItemStack(entry.resultItemId(), Math.max(1, entry.resultCount()));
        if (!main.isEmpty()) {
            guiGraphics.renderItem(main, x, y);
        } else {
            guiGraphics.fill(x, y, x + 16, y + 16, 0x774C4C4C);
        }
    }

    private String extractBalanceAmount() {
        int index = this.balanceLabel.lastIndexOf(':');
        if (index >= 0 && index + 1 < this.balanceLabel.length()) {
            return this.balanceLabel.substring(index + 1).trim();
        }
        return "";
    }

    private String extractBalanceLabel() {
        int index = this.balanceLabel.lastIndexOf(':');
        if (index > 0) {
            return this.balanceLabel.substring(0, index).trim();
        }
        return this.balanceLabel;
    }

    private int countPlayerItems(String itemId) {
        ItemStack targetStack = resolveItemStack(itemId, 1);
        if (targetStack.isEmpty() || this.minecraft == null || this.minecraft.player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : this.minecraft.player.getInventory().offhand) {
            if (ItemStack.isSameItemSameTags(stack, targetStack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void renderFrameChrome(GuiGraphics guiGraphics, Layout layout) {
        int panelX = layout.panelX();
        int panelY = layout.panelY();
        int right = panelX + layout.panelWidth();
        int bottom = panelY + layout.panelHeight();
        int borderColor = 0x9D4D7CA9;
        guiGraphics.fill(panelX, panelY, right, panelY + 1, borderColor);
        guiGraphics.fill(panelX, bottom - 1, right, bottom, borderColor);
        guiGraphics.fill(panelX, panelY, panelX + 1, bottom, borderColor);
        guiGraphics.fill(right - 1, panelY, right, bottom, borderColor);
    }

    private void renderEmptyCard(GuiGraphics guiGraphics, Font font, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0x3C09111A);
        guiGraphics.fill(x, y, x + width, y + 1, 0x2E39526E);
        guiGraphics.fill(x, y, x + 1, y + height, 0x2A39526E);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x2A39526E);
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0x2239526E);
        drawCardCorners(guiGraphics, x, y, width, height, false);
        String emptyText = "空槽位";
        int textX = x + Math.max(8, (width - font.width(emptyText)) / 2);
        int textY = y + (height / 2) - 4;
        guiGraphics.drawString(font, emptyText, textX, textY, 0x6F89A7, false);
    }

    private void drawCardCorners(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean selected) {
        int color = selected ? 0x7A8FC8FF : 0x334D6A86;
        guiGraphics.fill(x + 6, y + 6, x + 10, y + 7, color);
        guiGraphics.fill(x + 6, y + 6, x + 7, y + 10, color);
        guiGraphics.fill(x + width - 10, y + 6, x + width - 6, y + 7, color);
        guiGraphics.fill(x + width - 7, y + 6, x + width - 6, y + 10, color);
        guiGraphics.fill(x + 6, y + height - 7, x + 10, y + height - 6, color);
        guiGraphics.fill(x + 6, y + height - 10, x + 7, y + height - 6, color);
        guiGraphics.fill(x + width - 10, y + height - 7, x + width - 6, y + height - 6, color);
        guiGraphics.fill(x + width - 7, y + height - 10, x + width - 6, y + height - 6, color);
    }

    private void renderCompactCostStrip(GuiGraphics guiGraphics, Font font, WalletShopOfferEntry entry, int x, int y, int maxWidth) {
        int iconX = x;
        iconX = renderCostIcon(guiGraphics, font, entry.primaryCostItemId(), entry.primaryCostCount(), iconX, y);
        if (!entry.secondaryCostItemId().isBlank() && entry.secondaryCostCount() > 0 && iconX < x + maxWidth - 18) {
            guiGraphics.drawString(font, "+", iconX, y + 4, 0xCFE7FF, false);
            iconX = renderCostIcon(guiGraphics, font, entry.secondaryCostItemId(), entry.secondaryCostCount(), iconX + 9, y);
        }
    }

    private int renderCostIcon(GuiGraphics guiGraphics, Font font, String itemId, int count, int x, int y) {
        ItemStack stack = resolveItemStack(itemId, Math.max(1, count));
        if (stack.isEmpty()) {
            guiGraphics.fill(x, y, x + 14, y + 14, 0x663C4A5D);
            return x + 18;
        }
        guiGraphics.renderItem(stack, x, y - 1);
        if (count > 1) {
            guiGraphics.drawString(font, Integer.toString(count), x + 12, y + 7, 0xD7ECFF, false);
        }
        return x + 22;
    }

    private void renderStatusBadge(GuiGraphics guiGraphics, Font font, WalletShopOfferEntry entry, int x, int y) {
        renderStatusBadge(guiGraphics, font, entry, x, y, 42);
    }

    private void renderStatusBadge(GuiGraphics guiGraphics, Font font, WalletShopOfferEntry entry, int x, int y, int width) {
        int badgeColor = statusFill(entry.statusBadge());
        int textColor = statusTextColor(entry.statusBadge());
        String text = statusLabel(entry.statusBadge());
        guiGraphics.fill(x, y, x + width, y + 14, badgeColor);
        int textX = x + Math.max(4, (width - font.width(text)) / 2);
        guiGraphics.drawString(font, text, textX, y + 4, textColor, false);
    }

    private void drawScaledString(GuiGraphics guiGraphics, Font font, String text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.drawString(font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    private void renderSelectedSummary(GuiGraphics guiGraphics, Font font, Layout layout, int x, int y, int width) {
        WalletShopOfferEntry selected = selectedOffer();
        if (selected == null) {
            return;
        }
        int summaryHeight = layout.summaryHeight();
        guiGraphics.fill(x, y, x + width, y + summaryHeight, 0x280E1622);
        guiGraphics.fill(x, y, x + width, y + 1, 0x4F6B87A6);
        ItemStack stack = resolveItemStack(selected.resultItemId(), Math.max(1, selected.resultCount()));
        if (!stack.isEmpty()) {
            guiGraphics.fill(x + 8, y + 9, x + 30, y + 31, 0x330F1722);
            guiGraphics.renderItem(stack, x + 11, y + 12);
        }
        guiGraphics.drawString(font, "选中商品", x + 36, y + 6, 0xBED7F8, false);
        guiGraphics.drawString(font, font.plainSubstrByWidth(displayTitleWithCount(selected), Math.max(40, width - 96)), x + 36, y + 18, 0xF3D99A, false);
        drawScaledString(guiGraphics, font, font.plainSubstrByWidth(selected.resultItemId(), Math.max(20, Math.round((width - 96) / 0.72F))), x + 36, y + 31, 0xB8CBE7, 0.72F);
        renderStatusBadge(guiGraphics, font, selected, x + width - (layout.compact() ? 46 : 50), y + 6);
        renderCompactCostStrip(guiGraphics, font, selected, x + 8, y + summaryHeight - 24, Math.max(40, width - 70));
    }

    private void renderDetailPanel(GuiGraphics guiGraphics, Font font, Layout layout, int x, int y, int width, int height) {
        WalletShopOfferEntry selected = selectedOffer();
        int pulseGlow = pulseColor(0x102A5178, 0x2A4B7FB3);
        guiGraphics.fill(x, y, x + width, y + height, 0x86070D15);
        guiGraphics.fill(x, y, x + width, y + 2, 0x9F3E6A96);
        guiGraphics.fill(x, y, x + 2, y + height, 0x8B3E6A96);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, 0x8B3E6A96);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, 0x6A274462);
        guiGraphics.fill(x + 4, y + 4, x + width - 4, y + height - 4, 0x540A1018);
        guiGraphics.fill(x + 5, y + 5, x + width - 5, y + 7, pulseGlow);
        drawPanelBrackets(guiGraphics, x, y, width, height, 0x8FC8FF);
        guiGraphics.fill(x + 10, y + 28, x + width - 10, y + 29, 0x345D85B2);
        guiGraphics.drawString(font, "终端卷宗", x + 12, y + 10, 0xD4EAFF, false);
        guiGraphics.drawString(font, "ARCHIVE-ENTRY", x + width - 92, y + 10, 0x7395BA, false);
        if (selected == null) {
            guiGraphics.drawString(font, "暂无可查看的商品。", x + 12, y + 42, 0xAEC8E2, false);
            return;
        }

        int iconBoxX = x + 12;
        int iconBoxY = y + 40;
        int iconBoxSize = 42;
        guiGraphics.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxSize, iconBoxY + iconBoxSize, 0x251B2B40);
        guiGraphics.fill(iconBoxX, iconBoxY, iconBoxX + iconBoxSize, iconBoxY + 1, 0x4C6B87A6);
        guiGraphics.fill(iconBoxX, iconBoxY, iconBoxX + 1, iconBoxY + iconBoxSize, 0x4C6B87A6);
        guiGraphics.fill(iconBoxX + 5, iconBoxY + 5, iconBoxX + iconBoxSize - 5, iconBoxY + iconBoxSize - 5, 0x160A121A);
        guiGraphics.fill(iconBoxX + 2, iconBoxY + 2, iconBoxX + iconBoxSize - 2, iconBoxY + 4, pulseColor(0x184E7AA8, 0x3C7AAEE2));
        renderResultIcon(guiGraphics, selected, iconBoxX + 13, iconBoxY + 13);

        int textX = iconBoxX + iconBoxSize + 10;
        int textWidth = width - (textX - x) - 12;
        guiGraphics.drawString(font, font.plainSubstrByWidth(displayTitleWithCount(selected), textWidth), textX, iconBoxY + 3, 0xF2DC9D, false);
        drawScaledString(guiGraphics, font, font.plainSubstrByWidth(selected.resultItemId(), Math.max(24, Math.round(textWidth / ITEM_ID_SCALE))), textX, iconBoxY + 17, 0x9AB7D8, ITEM_ID_SCALE);
        String categoryText = selected.categoryTitle().isBlank() ? "未归类" : selected.categoryTitle();
        drawDetailChip(guiGraphics, font, textX, iconBoxY + 29, categoryText, 0x243248, 0x9ABEE5);
        drawDetailChip(guiGraphics, font, textX + Math.min(textWidth - 52, font.width(categoryText) + 22), iconBoxY + 29, statusLabel(selected.statusBadge()), statusFill(selected.statusBadge()), statusTextColor(selected.statusBadge()));

        int contentY = iconBoxY + iconBoxSize + 12;
        int detailWidth = width - 24;
        contentY = drawDetailBlock(guiGraphics, font, x + 12, contentY, detailWidth, "说明", selected.description().isBlank() ? List.of("暂无额外说明。") : wrapLines(font, selected.description(), detailWidth - 12, 3), 0xBED7F8, 0xAEC8E2);
        int cardGap = 8;
        int halfWidth = (detailWidth - cardGap) / 2;
        int cardBottom = Math.max(
                drawDetailSummaryCard(guiGraphics, font, x + 12, contentY, halfWidth, "奖励档案", selected.rewardSummary().isBlank() ? resolveDisplayTitle(selected) : selected.rewardSummary(), 0xF0D8A6, 0xD7ECFF, selected.resultItemId(), selected.resultCount()),
                drawDetailSummaryCard(guiGraphics, font, x + 12 + halfWidth + cardGap, contentY, halfWidth, "成本档案", selected.costSummary().isBlank() ? "无需成本。" : selected.costSummary(), 0x9FD0FF, 0xD7ECFF, selected.primaryCostItemId(), selected.primaryCostCount())
        );
        contentY = cardBottom + 8;
        contentY = drawDetailBlock(guiGraphics, font, x + 12, contentY, detailWidth, "状态", wrapLines(font, selected.stateText().isBlank() ? "等待进一步状态同步。" : selected.stateText(), detailWidth - 12, 2), 0xA6E5BE, 0xD7ECFF);

        int hintTop = Math.min(y + height - 42, contentY + 4);
        guiGraphics.fill(x + 12, hintTop, x + width - 12, hintTop + 24, 0x24131E2D);
        guiGraphics.fill(x + 12, hintTop, x + 14, hintTop + 24, selected.unlocked() && selected.affordable() ? pulseColor(0x6F8FC8FF, 0xB38FC8FF) : 0x7D6E85A0);
        String hint = selected.unlocked() && selected.affordable()
                ? (selected.walletPayment() ? "Enter 立即购入当前选中商品" : "Enter 立即交换当前选中商品")
                : "当前条目不可执行，先满足解锁或成本条件";
        guiGraphics.drawString(font, font.plainSubstrByWidth(hint, width - 32), x + 20, hintTop + 8, 0xD6EAFF, false);
    }

    private String resolveDisplayTitle(WalletShopOfferEntry entry) {
        ItemStack stack = resolveItemStack(entry.resultItemId(), Math.max(1, entry.resultCount()));
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        return entry.title();
    }

    private String displayTitleWithCount(WalletShopOfferEntry entry) {
        String title = resolveDisplayTitle(entry);
        return entry.resultCount() > 1 ? title + " x" + entry.resultCount() : title;
    }

    private int drawDetailBlock(GuiGraphics guiGraphics, Font font, int x, int y, int width, String title, List<String> lines, int titleColor, int bodyColor) {
        int lineCount = Math.max(1, lines.size());
        int height = 20 + lineCount * 11;
        guiGraphics.fill(x, y, x + width, y + height, 0x200E1622);
        guiGraphics.fill(x, y, x + width, y + 1, 0x334D6A86);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x162E4A66);
        guiGraphics.drawString(font, title, x + 8, y + 6, titleColor, false);
        int lineY = y + 18;
        for (String line : lines) {
            guiGraphics.drawString(font, line, x + 8, lineY, bodyColor, false);
            lineY += 11;
        }
        return y + height + 8;
    }

    private int drawDetailSummaryCard(GuiGraphics guiGraphics, Font font, int x, int y, int width, String title, String body, int titleColor, int bodyColor, String itemId, int itemCount) {
        int height = 44;
        guiGraphics.fill(x, y, x + width, y + height, 0x26111A25);
        guiGraphics.fill(x, y, x + width, y + 1, 0x41688EB9);
        guiGraphics.fill(x, y, x + 1, y + height, 0x2A567699);
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0x2A567699);
        ItemStack stack = resolveItemStack(itemId, Math.max(1, itemCount));
        if (!stack.isEmpty()) {
            guiGraphics.fill(x + 8, y + 13, x + 28, y + 33, 0x22081118);
            guiGraphics.renderItem(stack, x + 10, y + 15);
        }
        guiGraphics.drawString(font, title, x + 8, y + 4, titleColor, false);
        int textX = x + 34;
        int textWidth = Math.max(28, width - 40);
        List<String> lines = wrapLines(font, body, textWidth, 2);
        int lineY = y + 17;
        for (String line : lines) {
            guiGraphics.drawString(font, line, textX, lineY, bodyColor, false);
            lineY += 11;
        }
        return y + height;
    }

    private void drawPanelBrackets(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        int alphaColor = (color & 0x00FFFFFF) | 0x66000000;
        drawCornerL(guiGraphics, x + 6, y + 6, alphaColor);
        drawCornerL(guiGraphics, x + width - 12, y + 6, alphaColor);
        drawCornerL(guiGraphics, x + 6, y + height - 12, alphaColor);
        drawCornerL(guiGraphics, x + width - 12, y + height - 12, alphaColor);
    }

    private int pulseColor(int minColor, int maxColor) {
        long tick = (System.currentTimeMillis() / 120L) % 12L;
        float progress = tick <= 6L ? tick / 6.0F : (12L - tick) / 6.0F;
        int minAlpha = (minColor >>> 24) & 0xFF;
        int maxAlpha = (maxColor >>> 24) & 0xFF;
        int alpha = (int) (minAlpha + (maxAlpha - minAlpha) * progress);
        return (alpha << 24) | (maxColor & 0x00FFFFFF);
    }

    private List<String> wrapLines(Font font, String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remaining = text == null ? "" : text.trim();
        if (remaining.isEmpty()) {
            return List.of("");
        }
        while (!remaining.isEmpty() && lines.size() < maxLines) {
            String line = font.plainSubstrByWidth(remaining, width);
            if (line.isEmpty()) {
                break;
            }
            lines.add(line);
            remaining = remaining.substring(line.length()).trim();
        }
        if (!remaining.isEmpty() && !lines.isEmpty()) {
            int last = lines.size() - 1;
            String tail = lines.get(last);
            lines.set(last, tail.length() > 1 ? tail.substring(0, tail.length() - 1) + "…" : "…");
        }
        return lines;
    }

    private void drawDetailChip(GuiGraphics guiGraphics, Font font, int x, int y, String text, int fillColor, int textColor) {
        int width = font.width(text) + 10;
        guiGraphics.fill(x, y, x + width, y + 14, fillColor);
        guiGraphics.drawString(font, text, x + 5, y + 4, textColor, false);
    }

    private int statusFill(String statusBadge) {
        return switch (statusBadge) {
            case WalletShopOfferEntry.STATUS_READY -> 0x21364A;
            case WalletShopOfferEntry.STATUS_SHORT -> 0x34445A;
            default -> 0x2A2436;
        };
    }

    private int statusTextColor(String statusBadge) {
        return switch (statusBadge) {
            case WalletShopOfferEntry.STATUS_READY -> 0x95D1FF;
            case WalletShopOfferEntry.STATUS_SHORT -> 0xBFD8F1;
            default -> 0xA89BC4;
        };
    }

    private String statusLabel(String statusBadge) {
        return switch (statusBadge) {
            case WalletShopOfferEntry.STATUS_READY -> "可交易";
            case WalletShopOfferEntry.STATUS_SHORT -> "不足";
            default -> "锁定";
        };
    }

    private int statusAccent(WalletShopOfferEntry entry) {
        if (entry.isReadyState()) {
            return 0x7FB9E6FF;
        }
        if (entry.isShortState()) {
            return 0x89DCC97A;
        }
        if (entry.isLockedState()) {
            return 0x6E6A7E96;
        }
        return 0x5A4D7CA9;
    }

    private static ItemStack resolveItemStack(String itemId, int count) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, count));
    }

    private static boolean isIn(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void drawCornerL(GuiGraphics guiGraphics, int x, int y, int color) {
        guiGraphics.fill(x, y, x + 6, y + 1, color);
        guiGraphics.fill(x, y, x + 1, y + 6, color);
    }

    private static List<CategoryTab> buildCategories(List<WalletShopOfferEntry> offers) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        counts.put("", offers.size());
        titles.put("", "全部");
        for (WalletShopOfferEntry entry : offers) {
            String categoryId = entry.categoryId();
            String categoryTitle = entry.categoryTitle().isBlank() ? "未归类" : entry.categoryTitle();
            counts.merge(categoryId, 1, Integer::sum);
            titles.putIfAbsent(categoryId, categoryTitle);
        }
        return counts.entrySet().stream()
                .map(entry -> new CategoryTab(entry.getKey(), titles.getOrDefault(entry.getKey(), "全部"), entry.getValue()))
                .toList();
    }

    private static List<WalletShopOfferEntry> deduplicateOffers(List<WalletShopOfferEntry> offers) {
        Map<String, WalletShopOfferEntry> unique = new LinkedHashMap<>();
        for (WalletShopOfferEntry offer : offers) {
            unique.putIfAbsent(offer.entryId(), offer);
        }
        return List.copyOf(unique.values());
    }

    private record CategoryTab(String categoryId, String title, int count) {
    }

    private Layout layout() {
        int panelWidth = Math.max(MIN_PANEL_WIDTH, Math.min(BASE_PANEL_WIDTH, this.width - 88));
        int panelHeight = Math.max(MIN_PANEL_HEIGHT, Math.min(BASE_PANEL_HEIGHT, this.height - 72));
        int panelX = Math.max(8, snap((this.width - panelWidth) / 2.0D));
        int panelY = Math.max(8, snap((this.height - panelHeight) / 2.0D));
        boolean compact = panelWidth < 620 || panelHeight < 300;
        int headerHeight = compact ? 38 : 42;
        int footerHeight = 42;
        int sidebarWidth = Math.max(116, Math.min(BASE_SIDEBAR_WIDTH, panelWidth / 4));
        int contentX = sidebarWidth + 34;
        int listTop = headerHeight + 26;
        int rowGap = compact ? 8 : 10;
        int contentWidth = panelWidth - contentX - 24;
        boolean showDetailPanel = !compact && contentWidth >= 420;
        int contentGap = showDetailPanel ? 14 : 0;
        int detailWidth = showDetailPanel ? Math.max(176, Math.min(196, contentWidth / 3)) : 0;
        int listWidth = Math.max(210, contentWidth - detailWidth - contentGap);
        int contentHeight = Math.max(120, panelHeight - listTop - footerHeight - 18);
        boolean showSelectedSummary = !showDetailPanel;
        int summaryHeight = showSelectedSummary ? 58 : 0;
        int summaryGap = showSelectedSummary ? 10 : 0;
        int columnCount = 2;
        int columnGap = 12;
        int listInsetLeft = 8;
        int listInsetRight = 20;
        int gridWidth = Math.max(200, listWidth - listInsetLeft - listInsetRight);
        int rowsPerPage = 2;
        int availableGridHeight = Math.max(92, contentHeight - summaryHeight - summaryGap);
        int rowHeight = Math.max(42, Math.min(compact ? 60 : 64, (availableGridHeight - rowGap) / rowsPerPage));
        int cardWidth = Math.max(92, (gridWidth - columnGap) / 2);
        int listHeight = rowHeight * rowsPerPage + rowGap * Math.max(0, rowsPerPage - 1);
        int summaryY = panelY + listTop + listHeight + summaryGap;
        return new Layout(panelX, panelY, panelWidth, panelHeight, headerHeight, footerHeight, sidebarWidth, contentX, listTop, rowHeight, rowGap, listWidth, rowsPerPage, compact, showSelectedSummary, showDetailPanel, detailWidth, contentGap, contentHeight, listHeight, summaryY, summaryHeight, columnCount, columnGap, cardWidth, listInsetLeft, listInsetRight, gridWidth);
    }

    private record Layout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int headerHeight,
            int footerHeight,
            int sidebarWidth,
            int contentX,
            int listTop,
            int rowHeight,
            int rowGap,
            int listWidth,
            int rowsPerPage,
            boolean compact,
            boolean showSelectedSummary,
            boolean showDetailPanel,
            int detailWidth,
            int contentGap,
            int contentHeight,
            int listHeight,
            int summaryY,
            int summaryHeight,
            int columnCount,
            int columnGap,
            int cardWidth,
            int listInsetLeft,
            int listInsetRight,
            int gridWidth
    ) {
        int visibleItemCount() {
            return this.rowsPerPage * this.columnCount;
        }
    }
}
