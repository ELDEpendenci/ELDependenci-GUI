package com.ericlam.mc.eldgui.component.factory;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.AbstractComponent;
import com.ericlam.mc.eldgui.component.AttributeController;
import com.ericlam.mc.eldgui.component.Component;

import java.util.function.Consumer;

public final class ELDGBukkitItemFactory extends AbstractComponentFactory<BukkitItemFactory> implements BukkitItemFactory {

    public ELDGBukkitItemFactory(ItemStackService itemStackService, AttributeController attributeController) {
        super(itemStackService, attributeController);
    }

    @Override
    protected void defaultProperties() {
    }

    @Override
    public Component build(ItemStackService.ItemFactory itemFactory) {
        // item is static
        return new AbstractComponent(attributeController, itemFactory) {
        };
    }

    @Override
    public BukkitItemFactory setupByItemFactory(Consumer<ItemStackService.ItemFactory> factoryConsumer) {
        return editItemByFactory(factoryConsumer);
    }
}
