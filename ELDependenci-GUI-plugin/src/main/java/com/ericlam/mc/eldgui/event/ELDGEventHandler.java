package com.ericlam.mc.eldgui.event;

import com.ericlam.mc.eldgui.ELDGView;
import com.ericlam.mc.eldgui.MVCInstallation;
import com.ericlam.mc.eldgui.view.BukkitRedirectView;
import com.ericlam.mc.eldgui.view.View;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

public abstract class ELDGEventHandler<A extends Annotation, E extends InventoryInteractEvent> {

    protected final Map<RequestMapping, Method> eventMap = new ConcurrentHashMap<>();
    private final Object uiController;
    private final MethodParseManager parseManager;
    private final ReturnTypeManager returnTypeManager;
    private final Map<Class<? extends Annotation>, MVCInstallation.QualifierFilter<?>> customQualifier;


    public ELDGEventHandler(Object controller,
                            MethodParseManager parseManager,
                            ReturnTypeManager returnTypeManager,
                            Map<Class<? extends Annotation>, MVCInstallation.QualifierFilter<? extends Annotation>> customQualifier) {
        this.uiController = controller;
        this.parseManager = parseManager;
        this.returnTypeManager = returnTypeManager;
        this.customQualifier = customQualifier;
        this.loadAllCommonHandlers(controller);
        this.loadAllHandlers(controller).forEach((k, v) -> eventMap.put(toRequestMapping(k), v));
    }

    private void loadAllCommonHandlers(Object controller) {
        Arrays.stream(controller.getClass().getDeclaredMethods()).parallel()
                .filter(m -> m.isAnnotationPresent(RequestMapping.class))
                .forEach(m -> eventMap.put(m.getAnnotation(RequestMapping.class), m));
    }

    protected abstract Map<A, Method> loadAllHandlers(Object controller);

    public void unloadAllHandlers() {
        eventMap.clear();
    }

    @SuppressWarnings("unchecked")
    public void onEventHandle(
            E e,
            Player player,
            ELDGView<?> eldgView
    ) throws Exception {
        final Inventory nativeInventory = eldgView.getNativeInventory();
        final Map<Character, List<Integer>> patternMasks = eldgView.getPatternMasks();
        final View<?> currentView = eldgView.getView();
        final Set<Character> cancellable = eldgView.getCancelMovePatterns();
        if (e.getWhoClicked() != player) return;
        if (!e.getViewers().contains(player)) return;
        if (e.getInventory() != nativeInventory) return;
        Optional<Character> chOpt = patternMasks.keySet().stream().filter(ch -> slotTrigger(patternMasks.get(ch), e)).findAny();
        if (chOpt.isEmpty()) return;
        final var patternClicked = chOpt.get();
        if (cancellable.contains(patternClicked)) {
            e.setCancelled(true);
        }
        Optional<Method> targetMethod = eventMap.entrySet()
                .parallelStream()
                .filter((en) -> {
                    var requestMapper = en.getKey();
                    if (e.isCancelled() && requestMapper.ignoreCancelled()) {
                        return false;
                    }
                    return requestMapper.pattern() == patternClicked
                            && requestMapper.event() == e.getClass()
                            && requestMapper.view() == currentView.getClass();
                })
                .filter((en) -> {
                    Method method = en.getValue();
                    return Arrays.stream(method.getDeclaredAnnotations())
                            .filter(a -> customQualifier.containsKey(a.annotationType()))
                            .allMatch(a -> ((MVCInstallation.QualifierFilter<A>)customQualifier.get(a.annotationType())).checkIsPass(e, patternClicked, (A)a));
                })
                .map(Map.Entry::getValue)
                .findFirst();
        if (targetMethod.isEmpty()) return;
        Method m = targetMethod.get();
        Object[] results = parseManager.getMethodParameters(m, e);
        var returnType = m.invoke(uiController, results);
        returnTypeManager.handleReturnResult(m.getGenericReturnType(), returnType);
    }

    protected abstract boolean slotTrigger(List<Integer> slots, E event);

    protected abstract RequestMapping toRequestMapping(A annotation);

}
