package com.ericlam.mc.eldgui.component;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Optional;

public final class Checkbox extends AbstractComponent implements Clickable {

    private final Material checkedIcon, uncheckedIcon;
    private final String checkedShow, uncheckedShow;
    private final boolean disabled;

    private boolean currentValue;

    public Checkbox(
            AttributeController attributeController,
            ItemStackService.ItemFactory itemFactory,
            Material checkedIcon,
            Material uncheckedIcon,
            String checkedShow,
            String uncheckedShow,
            boolean disabled
    ) {
        super(attributeController, itemFactory);
        this.checkedIcon = checkedIcon;
        this.uncheckedIcon = uncheckedIcon;
        this.checkedShow = checkedShow;
        this.uncheckedShow = uncheckedShow;
        this.currentValue = (boolean) Optional.ofNullable(attributeController.getAttribute(getItem(), AttributeController.VALUE_TAG)).orElse(false);
        this.disabled = disabled;
        itemFactory.lore("-> " + (currentValue ? checkedShow : uncheckedShow));
        itemFactory.lore("&cDisabled");
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        this.currentValue = !this.currentValue;
        attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, this.currentValue);
        itemFactory.lore(List.of("-> " + (this.currentValue ? checkedShow : uncheckedShow)));
        itemFactory.material(this.currentValue ? checkedIcon : uncheckedIcon);
        this.updateInventory();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

}
