package com.ericlam.mc.eldgui.component;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.CircuitIterator;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import com.ericlam.mc.eldgui.component.modifier.Listenable;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RGBSelector extends AbstractComponent implements Listenable<AsyncChatEvent>, Clickable {

    private final boolean disabled;
    private final String inputMessage;
    private final String invalidMessage;
    private final long maxWait;

    // red 1, green 2, blue 3
    private final Map<Integer, Integer> rgbMap = new HashMap<>();

    private final CircuitIterator<Integer> colorIterator = new CircuitIterator<>(List.of(1, 2, 3));
    private int currentSelect = colorIterator.next();

    public RGBSelector(
            AttributeController attributeController,
            ItemStackService.ItemFactory itemFactory,
            boolean disabled,
            String inputMessage,
            String invalidMessage,
            long maxWait
    ) {
        super(attributeController, itemFactory);
        this.disabled = disabled;
        this.inputMessage = inputMessage;
        this.invalidMessage = invalidMessage;
        this.maxWait = maxWait;
        Color color = (Color) Optional.ofNullable(attributeController.getAttribute(getItem(), AttributeController.VALUE_TAG)).orElse(Color.WHITE);
        this.rgbMap.put(1, color.getRed());
        this.rgbMap.put(2, color.getGreen());
        this.rgbMap.put(3, color.getBlue());
        this.updateItem(color);
    }

    @Override
    public boolean shouldActivate(InventoryClickEvent e) {
        return e.getClick() == ClickType.MIDDLE;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int value = this.rgbMap.getOrDefault(this.currentSelect, 0);
        if (event.isLeftClick()) {
            if (event.isShiftClick()) {
                this.currentSelect = colorIterator.previous();
            } else {
                if (value == 255) return;
                this.rgbMap.put(this.currentSelect, Math.min(255, value + 1));
            }
        } else if (event.isRightClick()) {
            if (event.isShiftClick()) {
                this.currentSelect = colorIterator.next();
            } else {
                if (value == 0) return;
                this.rgbMap.put(this.currentSelect, Math.max(0, value - 1));
            }
        } else {
            return;
        }
        int red = this.rgbMap.get(1);
        int green = this.rgbMap.get(2);
        int blue = this.rgbMap.get(3);
        Color updatedColor = Color.fromRGB(red, green, blue);
        attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, updatedColor);
        this.updateItem(updatedColor);
    }

    private void updateItem(Color color) {
        itemFactory.material(Material.LEATHER_CHESTPLATE);
        itemFactory.editItemMeta(meta -> ((LeatherArmorMeta) meta).setColor(color));
        itemFactory.lore(List.of("-> " +
                ChatColor.of(new java.awt.Color(color.asRGB())) +
                (currentSelect == 1 ? "[" + color.getRed() + "]" : color.getRed()) +
                ", " + (currentSelect == 2 ? "[" + color.getGreen() + "]" : color.getGreen()) +
                ", " + (currentSelect == 3 ? "[" + color.getBlue() + "]" : color.getBlue())
        ));
        this.updateInventory();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onListen(Player player) {
        player.sendMessage(inputMessage);
    }

    @Override
    public long getMaxWaitingTime() {
        return maxWait;
    }

    @Override
    public void callBack(AsyncChatEvent event) {
        String input = ((TextComponent) event.message()).content();
        Color color;
        try {
            if (input.startsWith("#") && input.length() == 7) {
                int rgb = Integer.parseInt(input.substring(1), 16);
                color = Color.fromRGB(rgb);
            } else if (input.split(" ").length == 3) {
                String[] args = input.split(" ");
                int red = Integer.parseInt(args[0]);
                int green = Integer.parseInt(args[1]);
                int blue = Integer.parseInt(args[2]);
                color = Color.fromRGB(red, green, blue);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            event.getPlayer().sendMessage(invalidMessage);
            return;
        }
        this.rgbMap.put(1, color.getRed());
        this.rgbMap.put(2, color.getGreen());
        this.rgbMap.put(3, color.getBlue());
        attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, color);
        this.updateItem(color);
    }

    @Override
    public Class<AsyncChatEvent> getEventClass() {
        return AsyncChatEvent.class;
    }
}
