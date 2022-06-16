package com.ericlam.mc.eldgui;

import com.ericlam.mc.eld.services.ConfigPoolService;
import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.AttributeController;
import com.ericlam.mc.eldgui.component.Component;
import com.ericlam.mc.eldgui.component.ComponentFactory;
import com.ericlam.mc.eldgui.component.modifier.Animatable;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import com.ericlam.mc.eldgui.component.modifier.Disable;
import com.ericlam.mc.eldgui.component.modifier.Listenable;
import com.ericlam.mc.eldgui.view.*;
import org.apache.commons.lang.text.StrSubstitutor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ELDGView<T> {


    private static final Logger LOGGER = LoggerFactory.getLogger(ELDGView.class);

    private final Inventory nativeInventory;
    private final View<T> view;

    private final InventoryContext inventoryContext = new InventoryContext();
    private final Map<Character, List<Integer>> patternMasks = new LinkedHashMap<>();
    private final Map<Character, List<Component>> componentMap = new HashMap<>();
    private final Map<String, Component> componentsIdMap = new HashMap<>();
    private final Map<Class<? extends ComponentFactory<?>>, ComponentFactory<?>> factoryMap = new HashMap<>();
    private final Set<Character> cancelMovePatterns = new HashSet<>();
    private final List<Listener> componentListeners = new ArrayList<>();
    private final ELDGPlugin eldgPlugin = ELDGPlugin.getPlugin(ELDGPlugin.class);


    private boolean doNotDestroyView = false;
    private BukkitTask waitingTask = null;

    private final BukkitView<View<T>, T> bukkitView;


    public ELDGView(
            BukkitView<View<T>, T> bukkitView,
            ConfigPoolService configPoolService,
            ItemStackService itemStackService,
            Map<Class<? extends ComponentFactory<?>>, Class<? extends ComponentFactory<?>>> componentFactory
    ) {
        this.bukkitView = bukkitView;
        componentFactory.forEach((f, impl) -> {
            try {
                var constructor = impl.getConstructor(ItemStackService.class, AttributeController.class);
                this.factoryMap.put(f, constructor.newInstance(itemStackService, inventoryContext));
            } catch (Exception e) {
                LOGGER.warn("Error while initializing component factory " + impl, e);
                LOGGER.warn("ComponentFactory must have a constructor with item stack service and attribute controller arguments");
            }
        });
        Class<View<T>> viewCls = bukkitView.getView();
        InventoryTemplate template;
        if (viewCls.isAnnotationPresent(UseTemplate.class)) {
            UseTemplate useTemplate = viewCls.getAnnotation(UseTemplate.class);
            var pool = configPoolService.getGroupConfig(useTemplate.groupResource());
            template = pool.findById(useTemplate.template()).orElseThrow(() -> new IllegalStateException("Cannot find template: " + useTemplate.template()));
        } else if (viewCls.isAnnotationPresent(ViewDescriptor.class)) {
            ViewDescriptor viewDescriptor = viewCls.getAnnotation(ViewDescriptor.class);
            template = new CodeInventoryTemplate(viewDescriptor);
        } else {
            throw new IllegalStateException("view is lack of either @UseTemplate or @ViewDescriptor annotation.");
        }
        InventoryTemplate inventoryTemplate = template;
        Map<String, Object> objectFieldMap = PersistDataUtils.reflectToMap(bukkitView.getModel());
        String inventoryTitle = StrSubstitutor.replace(template.name, objectFieldMap);
        this.nativeInventory = Bukkit.createInventory(null, template.rows * 9, ChatColor.translateAlternateColorCodes('&', inventoryTitle));
        this.view = this.initializeView(viewCls);
        this.renderFromTemplate(inventoryTemplate, itemStackService);
        this.view.renderView(bukkitView.getModel(), new ELDGUIContext());

    }


    public Inventory getNativeInventory() {
        return nativeInventory;
    }

    public View<?> getView() {
        return view;
    }

    public Set<Character> getCancelMovePatterns() {
        return cancelMovePatterns;
    }

    public List<Component> getComponents(char pattern) {
        return this.componentMap.getOrDefault(pattern, List.of());
    }

    public void destroyView() {
        this.patternMasks.clear();
        this.nativeInventory.clear();
        this.componentListeners.forEach(HandlerList::unregisterAll);
        if (waitingTask != null && !waitingTask.isCancelled()) waitingTask.cancel();
        this.componentMap.values().stream().flatMap(Collection::stream).filter(c -> c instanceof Animatable && ((Animatable) c).isAnimating()).forEach(animate -> ((Animatable) animate).stopAnimation());
    }

    // boolean: pass to controller or not
    public boolean handleComponentClick(InventoryClickEvent e) {
        if (this.nativeInventory != e.getClickedInventory()) return false;
        if (e.getSlotType() != InventoryType.SlotType.CONTAINER) return false;
        final ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return false;
        String clickedId = inventoryContext.getIdFromItem(clicked);
        Optional<Component> clickedComponent = Optional.ofNullable(componentsIdMap.get(clickedId));
        if (clickedComponent.isEmpty()) return true;
        final Component component = clickedComponent.get();
        if (component instanceof Disable && ((Disable) component).isDisabled()) {
            return true;
        }
        if (component instanceof Clickable) {
            ((Clickable) component).onClick(e);
        }
        if (component instanceof Listenable) {
            var listenable = (Listenable<? extends PlayerEvent>) component;
            e.setCancelled(true);
            if (listenable.shouldActivate(e)) {
                this.activateEventListener(listenable, e);
            }
            return false;
        } else {
            return true;
        }
    }

    public boolean isDoNotDestroyView() {
        return doNotDestroyView;
    }

    private <E extends PlayerEvent> void activateEventListener(
            Listenable<E> component,
            InventoryClickEvent e
    ) {
        Class<E> eventClass = component.getEventClass();
        var listener = new Listener() {
        };
        Runnable callback = () -> {
            HandlerList.unregisterAll(listener);
            componentListeners.remove(listener);
            e.getWhoClicked().openInventory(this.nativeInventory);
            this.doNotDestroyView = false;
            if (waitingTask != null && !waitingTask.isCancelled()) waitingTask.cancel();
            this.waitingTask = null;
        };
        this.componentListeners.add(listener);
        this.doNotDestroyView = true;
        e.getWhoClicked().closeInventory();
        Bukkit.getServer().getPluginManager()
                .registerEvent(
                        eventClass,
                        listener,
                        EventPriority.NORMAL, (listen, event) -> {
                            if (event.getClass() != eventClass) return;
                            E realEvent = eventClass.cast(event);
                            if (realEvent.getPlayer() != e.getWhoClicked()) return;
                            if (event instanceof Cancellable) {
                                ((Cancellable) event).setCancelled(true);
                            }
                            if (event.isAsynchronous()) {
                                Bukkit.getScheduler().runTask(eldgPlugin, () -> {
                                    component.callBack(realEvent);
                                    callback.run();
                                });
                            } else {
                                component.callBack(realEvent);
                                callback.run();
                            }
                        }, eldgPlugin);
        component.onListen((Player) e.getWhoClicked());
        this.waitingTask = Bukkit.getScheduler().runTaskLater(eldgPlugin, callback, component.getMaxWaitingTime());
    }

    public InventoryContext getEldgContext() {
        return inventoryContext;
    }

    public Map<Character, List<Integer>> getPatternMasks() {
        return patternMasks;
    }

    private View<T> initializeView(Class<View<T>> viewCls) {
        try {
            return viewCls.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("error while creating view. (view must be no-arg constructor)", e);
        }
    }

    private void renderFromTemplate(InventoryTemplate demoInventories, ItemStackService itemStackService) {
        this.patternMasks.clear();
        this.cancelMovePatterns.clear();
        int line = 0;
        if (demoInventories.pattern.size() != demoInventories.rows)
            throw new IllegalStateException("界面模版的rows數量跟pattern行數不同。");
        for (String mask : demoInventories.pattern) {
            var masks = Arrays.copyOf(mask.toCharArray(), 9);
            for (int i = 0; i < masks.length; i++) {
                patternMasks.putIfAbsent(masks[i], new ArrayList<>());
                final int slots = i + 9 * line;
                patternMasks.get(masks[i]).add(slots);
            }
            line++;
        }
        for (String pattern : demoInventories.items.keySet()) {
            if (!this.patternMasks.containsKey(pattern.charAt(0))) continue;
            var slots = this.patternMasks.get(pattern.charAt(0));
            var itemDescriptor = demoInventories.items.get(pattern);
            var itemBuilder = itemStackService
                    .build(itemDescriptor.material)
                    .amount(itemDescriptor.amount);
            if (!itemDescriptor.name.isBlank()) itemBuilder.display(itemDescriptor.name);
            if (!itemDescriptor.lore.isEmpty()) itemBuilder.lore(itemDescriptor.lore);
            if (itemDescriptor.glowing) {
                Arrays.stream(Enchantment.values())
                        .filter(e -> e.canEnchantItem(itemBuilder.getItem()))
                        .findAny()
                        .ifPresentOrElse(
                                e -> itemBuilder.enchant(e, e.getStartLevel()),
                                () -> LOGGER.warn("{} has no available enchantments.", itemBuilder.getItem().getType())
                        );
            }
            var item = itemBuilder.getItem();
            for (Integer slot : slots) {
                this.nativeInventory.setItem(slot, item);
            }
            if (itemDescriptor.cancelMove) {
                cancelMovePatterns.add(pattern.charAt(0));
            }
        }
    }

    private final class ELDGUIContext implements UIContext {

        @Override
        public PatternComponentBuilder pattern(char pattern) {
            return new PatternComponentFactory(pattern);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <F extends ComponentFactory<F>> F factory(Class<F> factoryCls) {
            return Optional.ofNullable(factoryMap.get(factoryCls)).map(f -> (F) f).orElseThrow(() -> new IllegalStateException("unknown component factory: " + factoryCls));
        }

        private class PatternComponentFactory implements PatternComponentBuilder {

            private final char pattern;

            private PatternComponentFactory(char pattern) {
                this.pattern = pattern;
            }


            @Override
            public PatternComponentBuilder fill(Component component) {
                componentMap.putIfAbsent(pattern, new ArrayList<>());
                componentMap.get(pattern).add(component);
                inventoryContext.fillItem(pattern, component);
                if (component instanceof Animatable) ((Animatable) component).startAnimation();
                if (component.getItem().hasItemMeta()) {
                    String id = inventoryContext.getIdFromItem(component.getItem());
                    componentsIdMap.put(id, component);
                }
                return this;
            }

            @Override
            public PatternComponentBuilder components(Component... components) {
                for (Component component : components) {
                    componentMap.putIfAbsent(pattern, new ArrayList<>());
                    if (!inventoryContext.addItem(pattern, component)) {
                        LOGGER.warn("無法在界面 {} 的 Pattern {} 中新增組件 {}, 位置已滿。",
                                view.getClass().getSimpleName(), pattern, component.getClass().getSimpleName()
                        );
                        return this;
                    }
                    componentMap.get(pattern).add(component);
                    if (component instanceof Animatable) ((Animatable) component).startAnimation();
                    if (component.getItem().hasItemMeta()) {
                        String id = inventoryContext.getIdFromItem(component.getItem());
                        componentsIdMap.put(id, component);
                    }
                }
                return this;
            }

            @Override
            public PatternComponentBuilder component(int pos, Component component) {
                componentMap.putIfAbsent(pattern, new ArrayList<>());
                if (!inventoryContext.setItem(pattern, pos, component)) {
                    LOGGER.warn("無法在界面 {} 的 Pattern {} 中設置組件 {} 到位置 {}, 此位置無效。",
                            view.getClass().getSimpleName(), pattern, component.getClass().getSimpleName(), pos
                    );
                    return this;
                }
                componentMap.get(pattern).add(component);
                if (component instanceof Animatable) ((Animatable) component).startAnimation();
                if (component.getItem().hasItemMeta()) {
                    String id = inventoryContext.getIdFromItem(component.getItem());
                    componentsIdMap.put(id, component);
                }
                return this;
            }

            @Override
            public PatternComponentBuilder bindAll(String key, Object value) {
                inventoryContext.setAttribute(pattern, key, value);
                return this;
            }

            @Override
            public UIContext and() {
                return ELDGUIContext.this;
            }
        }
    }


    @SuppressWarnings("unchecked")
    public final class InventoryContext implements AttributeController {

        private final Map<String, Map<String, Object>> attributeMap = new ConcurrentHashMap<>();

        // attributes

        public <C> C getAttributePrimitive(Class<C> type, ItemStack itemStack, String key) {
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            var o = PersistDataUtils.getPersistentDataType(type);
            return container.get(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o);
        }

        public Map<String, Object> getAsMap(ItemStack item) {
            String id = getAttributePrimitive(String.class, item, "id");
            return Optional.ofNullable(attributeMap.get(id)).map(HashMap::new).orElseGet(HashMap::new);
        }

        @Override
        public synchronized <C> C getAttribute(ItemStack item, String key) {
            // instead of using persist data type, use map
            // return getObjectAttribute(item, key);
            if (!item.hasItemMeta()) {
                LOGGER.warn("{} has no item meta, return null.", item.getType());
                return null;
            }
            String id = getIdFromItem(item);
            attributeMap.putIfAbsent(id, new HashMap<>());
            LOGGER.debug("item (" + item.getType() + ") is now: " + getAsMap(item).toString());
            return (C) attributeMap.get(id).get(key);
        }

        private String getIdFromItem(ItemStack item) {
            String id = getAttributePrimitive(String.class, item, "id");
            if (id == null) {
                id = UUID.randomUUID().toString();
                this.setAttributePrimitive(String.class, item, "id", id);
            }
            return id;
        }

        public <C> C getObjectAttribute(ItemStack itemStack, String key) {
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            var o = PersistDataUtils.getPersistentDataType();
            return (C) container.get(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o);
        }

        public <C> void setObjectAttribute(ItemStack itemStack, String key, C value) {
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            var o = PersistDataUtils.getPersistentDataType();
            container.set(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o, value);
            itemStack.setItemMeta(meta);
        }


        public <C> void setAttributePrimitive(Class<C> type, ItemStack itemStack, String key, Object value) {
            C realValue;
            try {
                realValue = type.cast(value);
            } catch (ClassCastException e) {
                LOGGER.warn("value " + value + "'s type is not " + type, e);
                return;
            }
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            var o = PersistDataUtils.getPersistentDataType(type);
            container.set(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o, realValue);
            itemStack.setItemMeta(meta);
        }

        @Override
        public synchronized void setAttribute(ItemStack itemStack, String key, Object value) {
            // instead of using persist data type, use map
            //this.setObjectAttribute(itemStack, key, value);
            String id = getIdFromItem(itemStack);
            this.attributeMap.putIfAbsent(id, new HashMap<>());
            this.attributeMap.get(id).put(key, value);

            LOGGER.debug("item (" + itemStack.getType() + ") is now: " + getAsMap(itemStack).toString());
        }


        public <C> void setAttributePrimitive(Class<C> type, char pattern, String key, Object value) {
            getItems(pattern).forEach(item -> this.setAttributePrimitive(type, item, key, value));
        }


        @Override
        public synchronized void setAttribute(char pattern, String key, Object value) {
            getItems(pattern).forEach(item -> setAttribute(item, key, value));
        }


        // inventory control

        public boolean setItem(char pattern, int slot, Component component) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return false;
            int order = 0;
            for (Integer s : slots) {
                if (order == slot) {
                    Runnable runner = () -> nativeInventory.setItem(s, component.getItem());
                    component.setUpdateHandler(runner);
                    runner.run();
                    return true;
                }
                order++;
            }
            return false;
        }

        public List<ItemStack> getItems(char pattern) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return List.of();
            List<ItemStack> items = new ArrayList<>();
            for (int s : slots) {
                Optional.ofNullable(nativeInventory.getItem(s)).ifPresent(items::add);
            }
            return List.copyOf(items);
        }

        public Map<Integer, ItemStack> getItemMap(char pattern) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return Map.of();
            Map<Integer, ItemStack> items = new HashMap<>();
            int order = 0;
            for (int s : slots) {
                var item = nativeInventory.getItem(s);
                if (item != null) {
                    items.put(order, item);
                }
                order++;
            }
            return Map.copyOf(items);
        }

        public boolean addItem(char pattern, Component component) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return false;
            for (Integer s : slots) {
                var slotItem = nativeInventory.getItem(s);
                if (slotItem != null && slotItem.getType() != Material.AIR) continue;
                Runnable runner = () -> nativeInventory.setItem(s, component.getItem());
                component.setUpdateHandler(runner);
                runner.run();
                return true;
            }
            return false;
        }

        public void fillItem(char pattern, Component component) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return;
            Runnable runner = () -> {
                for (Integer s : slots) {
                    nativeInventory.setItem(s, component.getItem());
                }
            };
            component.setUpdateHandler(runner);
            runner.run();
        }

    }

    public BukkitView<View<T>, T> getBukkitView() {
        return bukkitView;
    }
}
