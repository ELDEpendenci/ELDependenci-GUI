package com.ericlam.mc.eldgui;

import com.ericlam.mc.eld.services.ConfigPoolService;
import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.controller.UIRequest;
import com.ericlam.mc.eldgui.view.BukkitView;
import com.ericlam.mc.eldgui.view.UseTemplate;
import com.ericlam.mc.eldgui.view.View;
import com.ericlam.mc.eldgui.view.ViewDescriptor;
import com.google.inject.TypeLiteral;
import org.apache.commons.lang.text.StrSubstitutor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public final class ELDGView<T> {

    private final Inventory nativeInventory;
    private final Map<Character, List<Integer>> patternMasks = new LinkedHashMap<>();
    private final Set<Character> cancelMovePatterns = new HashSet<>();
    private final View<T> view;
    private final ELDGContext eldgContext = new ELDGContext();


    public ELDGView(BukkitView<View<T>, T> bukkitView, ConfigPoolService configPoolService, ItemStackService itemStackService) {
        Class<View<T>> viewCls = bukkitView.getView();
        InventoryTemplate template;
        if (viewCls.isAnnotationPresent(UseTemplate.class)) {
            UseTemplate useTemplate = viewCls.getAnnotation(UseTemplate.class);
            var pool = configPoolService.getPool(useTemplate.groupResource());
            if (pool == null)
                throw new IllegalStateException("config pool is not loaded: " + useTemplate.groupResource());
            template = Optional.ofNullable(pool.get(useTemplate.template())).orElseThrow(() -> new IllegalStateException("Cannot find template: " + useTemplate.template()));
        } else if (viewCls.isAnnotationPresent(ViewDescriptor.class)) {
            ViewDescriptor viewDescriptor = viewCls.getAnnotation(ViewDescriptor.class);
            template = new CodeInventoryTemplate(viewDescriptor);
        } else {
            throw new IllegalStateException("view is lack of either @UseTemplate or @ViewDescriptor annotation.");
        }
        InventoryTemplate inventoryTemplate = template;
        Map<String, Object> objectFieldMap = objectFieldsToMap(bukkitView.getModel());
        String inventoryTitle = StrSubstitutor.replace(template.name, objectFieldMap);
        this.nativeInventory = Bukkit.createInventory(null, template.rows * 9, ChatColor.translateAlternateColorCodes('&', inventoryTitle));
        this.view = this.initializeView(viewCls);
        // === test only ===
        this.view.setItemStackService(itemStackService);
        // ========
        this.nativeInventory.clear();
        this.renderFromTemplate(inventoryTemplate, itemStackService);
        this.view.renderView(bukkitView.getModel(), eldgContext);
    }

    private Map<String, Object> objectFieldsToMap(Object model){
        return Arrays.stream(model.getClass().getFields()).collect(Collectors.toMap(Field::getName, f -> {
           try {
               f.setAccessible(true);
               return f.get(model);
           }catch (Exception e){
               e.printStackTrace();
               return "[Error: "+e.getClass().getSimpleName()+"]";
           }
        }));
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

    public void destroyView(){
        this.patternMasks.clear();
        this.nativeInventory.clear();
    }

    public ELDGContext getEldgContext() {
        return eldgContext;
    }

    public Map<Character, List<Integer>> getPatternMasks() {
        return patternMasks;
    }

    private View<T> initializeView(Class<View<T>> viewCls){
        try{
            return viewCls.getConstructor().newInstance();
        }catch (Exception e) {
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
            if (itemDescriptor.glowing) itemBuilder.enchant(Enchantment.DURABILITY, 1);
            var item = itemBuilder.getItem();
            for (Integer slot : slots) {
                this.nativeInventory.setItem(slot, item);
            }
            if (itemDescriptor.cancelMove) {
                cancelMovePatterns.add(pattern.charAt(0));
            }
        }
    }



    public final class ELDGContext implements UIContext, UIRequest {

        @Override
        public boolean setItem(char pattern, int slot, ItemStack itemStack) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return false;
            int order = 0;
            for (Integer s : slots) {
                if (order == slot) {
                    nativeInventory.setItem(s, itemStack);
                    return true;
                }
                order++;
            }
            return false;
        }

        public <C> C getAttribute(Class<C> type, ItemStack itemStack, String key) {
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            try {
                var con = PersistentDataType.PrimitivePersistentDataType.class.getDeclaredConstructor(type);
                con.setAccessible(true);
                PersistentDataType<C, C> o = con.newInstance(type);
                return container.get(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public <C> void setAttribute(Class<C> type, ItemStack itemStack, String key, C value) {
            var meta = itemStack.getItemMeta();
            if (meta == null)
                throw new IllegalStateException("cannot get attribute: " + key + ", this item has no item meta.");
            var container = meta.getPersistentDataContainer();
            try {
                var con = PersistentDataType.PrimitivePersistentDataType.class.getDeclaredConstructor(type);
                con.setAccessible(true);
                PersistentDataType<C, C> o = con.newInstance(type);
                container.set(new NamespacedKey(ELDGPlugin.getPlugin(ELDGPlugin.class), key), o, value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public <C> void setAttribute(Class<C> type, char pattern, String key, C value) {
            getItems(pattern).forEach(item -> this.setAttribute(type, item, key, value));
        }

        public List<ItemStack> getItems(char pattern) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return List.of();
            List<ItemStack> items = new ArrayList<>();
            for (int s : slots) {
                var item = Optional.ofNullable(nativeInventory.getItem(s)).orElseGet(() -> new ItemStack(Material.AIR));
                items.add(item);
            }
            return List.copyOf(items);
        }

        @Override
        public boolean addItem(char pattern, ItemStack itemStack) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return false;
            for (Integer s : slots) {
                var slotItem = nativeInventory.getItem(s);
                if (slotItem != null && slotItem.getType() != Material.AIR) continue;
                nativeInventory.setItem(s, itemStack);
                return true;
            }
            return false;
        }

        @Override
        public void fillItem(char pattern, ItemStack itemStack) {
            var slots = patternMasks.get(pattern);
            if (slots == null) return;
            for (Integer s : slots) {
                nativeInventory.setItem(s, itemStack);
            }
        }

    }
}
