package com.ericlam.mc.eldgui.component.factory;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.AttributeController;
import com.ericlam.mc.eldgui.component.Component;
import com.ericlam.mc.eldgui.component.NumInputField;

import java.util.Optional;

public final class ELDGNumInputFactory extends AbstractComponentFactory<NumInputFactory> implements NumInputFactory {

    private boolean disabled;
    private String inputMessage;
    private String errorMessage;
    private long waitInput;
    private ELDNumberTypeFactory<?> typeFactory = null;

    public ELDGNumInputFactory(ItemStackService itemStackService, AttributeController attributeController) {
        super(itemStackService, attributeController);
    }

    @Override
    protected void defaultProperties() {
        this.disabled = false;
        this.inputMessage = "Input a number between ${min} to ${max}";
        this.errorMessage = "The number you input is not valid.";
        this.waitInput = 200L;
    }

    @Override
    public Component build(ItemStackService.ItemFactory itemFactory) {
        return Optional.ofNullable(typeFactory).map(t -> t.build(itemFactory)).orElseThrow(() -> new IllegalStateException("你尚未綁定數字類型!"));
    }

    @Override
    public NumInputFactory label(String label) {
        editItemByFactory(factory -> factory.display(label));
        return this;
    }

    @Override
    public <T extends Number> NumberTypeFactory<T> useNumberType(Class<T> type) {
        var f = new ELDNumberTypeFactory<>(type);
        this.typeFactory = f;
        return f;
    }


    @Override
    public NumInputFactory waitForInput(long wait) {
        this.waitInput = wait;
        return this;
    }

    @Override
    public NumInputFactory messageInput(String message) {
        this.inputMessage = message;
        return this;
    }

    @Override
    public NumInputFactory messageInvalidNumber(String message) {
        this.errorMessage = message;
        return this;
    }

    @Override
    public NumInputFactory disabled() {
        this.disabled = true;
        return this;
    }

    class ELDNumberTypeFactory<T extends Number> implements NumInputFactory.NumberTypeFactory<T> {

        private T min, max, step;
        private final Class<T> numberType;

        ELDNumberTypeFactory(Class<T> numberType) {
            this.numberType = numberType;
        }

        @Override
        public NumInputFactory.NumberTypeFactory<T> min(T min) {
            this.min = min;
            return this;
        }

        @Override
        public NumInputFactory.NumberTypeFactory<T> max(T max) {
            this.max = max;
            return this;
        }

        @Override
        public NumInputFactory.NumberTypeFactory<T> step(T step) {
            this.step = step;
            return this;
        }

        @Override
        public NumInputFactory.NumberTypeFactory<T> bindInput(String field, T initValue) {
            bind(AttributeController.FIELD_TAG, field);
            bind(AttributeController.VALUE_TAG, initValue);
            return this;
        }

        public Component build(ItemStackService.ItemFactory itemFactory) {
            return new NumInputField<T>(
                    attributeController,
                    itemFactory,
                    min,
                    max,
                    step,
                    disabled,
                    inputMessage,
                    errorMessage,
                    waitInput,
                    numberType
            );
        }

        @Override
        public NumInputFactory then() {
            return ELDGNumInputFactory.this;
        }
    }
}
