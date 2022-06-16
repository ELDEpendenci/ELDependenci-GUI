package com.ericlam.mc.eldgui.component.factory;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.AttributeController;
import com.ericlam.mc.eldgui.component.Component;
import com.ericlam.mc.eldgui.component.Selection;
import org.bukkit.Material;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ELDGSelectionFactory extends AbstractComponentFactory<SelectionFactory> implements SelectionFactory {

    private ELDGSelectionSettings<?> selectionSettings;
    private boolean disabled;

    public ELDGSelectionFactory(ItemStackService itemStackService, AttributeController attributeController) {
        super(itemStackService, attributeController);
    }

    @Override
    protected void defaultProperties() {
        this.disabled = false;
        this.selectionSettings = new ELDGSelectionSettings<>(List.of(), Map.of(), Map.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public Component build(ItemStackService.ItemFactory itemFactory) {
        if (selectionSettings.getElements().isEmpty())
            throw new IllegalStateException("Selection Elements cannot be empty.");
        return new Selection(attributeController, itemFactory, disabled, selectionSettings);
    }

    @Override
    public SelectionFactory label(String title) {
        return editItemByFactory(f -> f.display(title));
    }

    @Override
    public SelectionFactory disabled() {
        this.disabled = true;
        return editItemByFactory(f -> f.lore("&cDisabled"));
    }

    @Override
    public <T> SelectionSettings<T> selectable(List<T> selections) {
        return new ELDGSelectionSettings<>(selections, Map.of(), Map.of());
    }

    @Override
    public <T> SelectionSettings<T> selectable(Class<T> type, Consumer<SelectionBuilder<T>> selectionBuilder) {
        ELDGSelectionBuilder<T> builder = new ELDGSelectionBuilder<>();
        selectionBuilder.accept(builder);
        return new ELDGSelectionSettings<>(builder.elements, builder.icons, builder.amounts);
    }


    private static final class ELDGSelectionBuilder<T> implements SelectionBuilder<T> {

        private final List<T> elements = new ArrayList<>();
        private final Map<T, Material> icons = new HashMap<>();
        private final Map<T, Integer> amounts = new HashMap<>();

        @Override
        public Selection<T> insert(T element) {
            return new ELDGSelection(element);
        }

        private final class ELDGSelection implements SelectionBuilder.Selection<T> {

            private final T element;
            private int amount = -1;
            private Material icon = null;

            private ELDGSelection(T element) {
                this.element = element;
            }


            @Override
            public SelectionBuilder.Selection<T> number(int amount) {
                this.amount = amount;
                return this;
            }

            @Override
            public SelectionBuilder.Selection<T> icon(Material icon) {
                this.icon = icon;
                return this;
            }

            @Override
            public void submit() {
                elements.add(element);
                if (amount != -1) {
                    amounts.put(element, amount);
                }
                if (icon != null) {
                    icons.put(element, icon);
                }
            }
        }
    }

    public final class ELDGSelectionSettings<T> implements SelectionSettings<T> {

        private final List<T> elements;
        private final Map<T, Material> icons;
        private final Map<T, Integer> amounts;
        private Function<T, String> toText = Objects::toString;

        private T initValue = null;


        private ELDGSelectionSettings(
                List<T> elements,
                Map<T, Material> icons,
                Map<T, Integer> amounts
        ) {
            this.elements = elements;
            this.icons = icons;
            this.amounts = amounts;
        }

        @Override
        public SelectionSettings<T> toDisplay(Function<T, String> toText) {
            this.toText = toText;
            return this;
        }

        @Override
        public SelectionSettings<T> bindInput(String field, T value) {
            bind(AttributeController.FIELD_TAG, field);
            bind(AttributeController.VALUE_TAG, value);
            this.initValue = value;
            return this;
        }

        @Override
        public SelectionFactory then() {
            editItemByFactory(f -> {
                f.lore(
                        this.elements
                                .stream()
                                .map(e -> {
                                    if (initValue == e) {
                                        return "&f&l- " + toText.apply(e);
                                    } else {
                                        return "&7- " + toText.apply(e);
                                    }
                                })
                                .collect(Collectors.toList())
                );
                if (initValue == null) return;
                if (amounts.containsKey(initValue)) {
                    f.amount(amounts.get(initValue));
                }
                if (icons.containsKey(initValue)) {
                    f.material(icons.get(initValue));
                }
            });
            selectionSettings = this;
            return ELDGSelectionFactory.this;
        }

        public Function<T, String> getToText() {
            return toText;
        }

        public List<T> getElements() {
            return elements;
        }

        public Map<T, Material> getIcons() {
            return icons;
        }

        public Map<T, Integer> getAmounts() {
            return amounts;
        }
    }
}
