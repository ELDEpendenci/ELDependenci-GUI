package com.ericlam.mc.eldgui.component.factory;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.AttributeController;
import com.ericlam.mc.eldgui.component.Component;
import com.ericlam.mc.eldgui.component.DateSelector;

import java.time.LocalDate;

public final class ELDGDateSelectorFactory extends AbstractComponentFactory<DateSelectorFactory> implements DateSelectorFactory {

    private String input;
    private String invalid;
    private boolean disabled;
    private long maxWait;

    public ELDGDateSelectorFactory(ItemStackService itemStackService, AttributeController attributeController) {
        super(itemStackService, attributeController);
    }

    @Override
    protected void defaultProperties() {
        this.input = "Input the value with format: <year> <month> <day>";
        this.invalid = "invalid format!";
        this.disabled = false;
        this.maxWait = 200L;
    }

    @Override
    public Component build(ItemStackService.ItemFactory itemFactory) {
        return new DateSelector(attributeController, itemFactory, input, invalid, disabled, maxWait);
    }

    @Override
    public DateSelectorFactory disabled() {
        this.disabled = true;
        return this;
    }

    @Override
    public DateSelectorFactory label(String title) {
        return editItemByFactory(f -> f.display(title));
    }

    @Override
    public DateSelectorFactory bindInput(String field, LocalDate initValue) {
        bind(AttributeController.FIELD_TAG, field);
        bind(AttributeController.VALUE_TAG, initValue);
        return this;
    }

    @Override
    public DateSelectorFactory inputMessage(String message) {
        this.input = message;
        return this;
    }

    @Override
    public DateSelectorFactory invalidMessage(String message) {
        this.invalid = message;
        return this;
    }

    @Override
    public DateSelectorFactory waitForInput(long maxWait) {
        this.maxWait = maxWait;
        return this;
    }
}
