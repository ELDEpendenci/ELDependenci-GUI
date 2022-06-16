package com.ericlam.mc.eldgui.component;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.CircuitIterator;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import com.ericlam.mc.eldgui.component.modifier.Listenable;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class DateSelector extends AbstractComponent implements Clickable, Listenable<AsyncChatEvent> {

    private final String input;
    private final String invalid;
    private final boolean disabled;
    private final long maxWait;

    private final Map<Integer, Consumer<InventoryClickEvent>> handlerMap = new HashMap<>();

    private final CircuitIterator<Integer> selector = new CircuitIterator<Integer>(List.of(1, 2, 3));
    private int currentSelect = selector.next();

    private LocalDate currentValue;

    public DateSelector(
            AttributeController attributeController,
            ItemStackService.ItemFactory itemFactory,
            String input,
            String invalid,
            boolean disabled,
            long maxWait
    ) {
        super(attributeController, itemFactory);
        this.input = input;
        this.invalid = invalid;
        this.disabled = disabled;
        this.maxWait = maxWait;
        this.currentValue = Optional.ofNullable((LocalDate) attributeController.getAttribute(getItem(), AttributeController.VALUE_TAG)).orElseGet(LocalDate::now);
        handlerMap.put(1, e -> {
            if (e.isLeftClick()) {
                if (!ChronoField.YEAR_OF_ERA.range().isValidIntValue(this.currentValue.getYear() + 1)) return;
                this.currentValue = this.currentValue.plusYears(1);
            } else if (e.isRightClick()) {
                if (!ChronoField.YEAR_OF_ERA.range().isValidIntValue(this.currentValue.getYear() - 1)) return;
                this.currentValue = this.currentValue.minusYears(1);
            }
        });
        handlerMap.put(2, e -> {
            if (e.isLeftClick()) {
                this.currentValue = this.currentValue.plusMonths(1);
            } else if (e.isRightClick()) {
                this.currentValue = this.currentValue.minusMonths(1);
            }
        });
        handlerMap.put(3, e -> {
            if (e.isLeftClick()) {
                this.currentValue = this.currentValue.plusDays(1);
            } else if (e.isRightClick()) {
                this.currentValue = this.currentValue.minusDays(1);
            }
        });
        this.updateItem();
    }

    private void updateItem() {
        int year = this.currentValue.getYear();
        int month = this.currentValue.getMonthValue();
        int day = this.currentValue.getDayOfMonth();

        itemFactory.lore(List.of("&7-> &f" +
                (currentSelect == 1 ? "[" + year + "]" : year) +
                "/" + (currentSelect == 2 ? "[" + month + "]" : month) +
                "/" + (currentSelect == 3 ? "[" + day + "]" : day)
        ));

        this.updateInventory();
    }

    @Override
    public boolean shouldActivate(InventoryClickEvent e) {
        return e.getClick() == ClickType.MIDDLE;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.isShiftClick()) {
            if (event.isLeftClick()) {
                this.currentSelect = selector.previous();
            } else if (event.isRightClick()) {
                this.currentSelect = selector.next();
            } else {
                return;
            }
        } else {
            try {
                this.handlerMap.get(currentSelect).accept(event);
            } catch (DateTimeException e) {
                event.getWhoClicked().sendMessage(e.getMessage());
                return;
            }
        }
        attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, this.currentValue);
        this.updateItem();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onListen(Player player) {
        player.sendMessage(input);
    }

    @Override
    public long getMaxWaitingTime() {
        return maxWait;
    }

    @Override
    public void callBack(AsyncChatEvent event) {
        String message = ((TextComponent) event.message()).content();
        try {
            String[] args = message.split(" ");
            int year = Integer.parseInt(args[0]);
            int month = Integer.parseInt(args[1]);
            int days = Integer.parseInt(args[2]);
            this.currentValue = LocalDate.of(year, month, days);
            attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, this.currentValue);
            this.updateItem();
        } catch (RuntimeException e) {
            event.getPlayer().sendMessage(invalid);
        }
    }

    @Override
    public Class<AsyncChatEvent> getEventClass() {
        return AsyncChatEvent.class;
    }
}
