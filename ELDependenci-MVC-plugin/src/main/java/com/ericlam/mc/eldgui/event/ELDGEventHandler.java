package com.ericlam.mc.eldgui.event;

import com.ericlam.mc.eldgui.ELDGView;
import com.ericlam.mc.eldgui.MVCInstallation;
import com.ericlam.mc.eldgui.manager.MethodParseManager;
import com.ericlam.mc.eldgui.manager.ReturnTypeManager;
import com.ericlam.mc.eldgui.middleware.MiddleWareManager;
import com.ericlam.mc.eldgui.view.AnyView;
import com.ericlam.mc.eldgui.view.BukkitView;
import com.ericlam.mc.eldgui.view.View;
import com.google.common.collect.ImmutableMap;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.inventory.Inventory;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ELDGEventHandler<A extends Annotation, E extends InventoryInteractEvent> {

    private static final Map<Class<?>, Map<RequestMapping, Method>> controllerEventMap = new ConcurrentHashMap<>();

    protected final Map<RequestMapping, Method> eventMap = new ConcurrentHashMap<>();
    private final Object uiController;
    private final MethodParseManager parseManager;
    private final ReturnTypeManager returnTypeManager;
    private final MiddleWareManager middleWareManager;
    private final Map<Class<? extends Annotation>, MVCInstallation.QualifierFilter<?>> customQualifier;


    public ELDGEventHandler(Object controller,
                            MethodParseManager parseManager,
                            ReturnTypeManager returnTypeManager,
                            MiddleWareManager middleWareManager,
                            Map<Class<? extends Annotation>, MVCInstallation.QualifierFilter<? extends Annotation>> customQualifier,
                            Method[] declaredMethods
    ) {
        this.uiController = controller;
        this.parseManager = parseManager;
        this.middleWareManager = middleWareManager;
        this.returnTypeManager = returnTypeManager;
        this.customQualifier = customQualifier;
        if (controllerEventMap.containsKey(controller.getClass())) {
            this.eventMap.putAll(controllerEventMap.get(controller.getClass()));
        } else {
            this.loadAllCommonHandlers(declaredMethods);
            this.loadAllHandlers(declaredMethods).forEach((k, v) -> eventMap.put(toRequestMapping(k), v));
            controllerEventMap.put(controller.getClass(), ImmutableMap.copyOf(eventMap));
        }
    }

    private void loadAllCommonHandlers(Method[] declareMethods) {
        Arrays.stream(declareMethods).parallel()
                .filter(m -> m.isAnnotationPresent(RequestMapping.class))
                .forEach(m -> eventMap.put(m.getAnnotation(RequestMapping.class), m));
    }

    protected abstract Map<A, Method> loadAllHandlers(Method[] declaredMethods);

    public void unloadAllHandlers() {
        eventMap.clear();
    }

    @SuppressWarnings("unchecked")
    public boolean onEventHandle(
            E e,
            Player player,
            ELDGView<?> eldgView
    ) throws Exception {
        final Inventory nativeInventory = eldgView.getNativeInventory();
        final Map<Character, List<Integer>> patternMasks = eldgView.getPatternMasks();
        final View<?> currentView = eldgView.getView();
        final Set<Character> cancellable = eldgView.getCancelMovePatterns();
        if (e.getWhoClicked() != player) return false;
        if (!e.getViewers().contains(player)) return false;
        if (e.getInventory() != nativeInventory) return false;
        Optional<Character> chOpt = patternMasks.keySet().stream().filter(ch -> slotTrigger(patternMasks.get(ch), e)).findAny();
        if (chOpt.isEmpty()) return false;
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
                            && (requestMapper.view() == AnyView.class || requestMapper.view() == currentView.getClass());
                })
                .filter((en) -> {
                    Method method = en.getValue();
                    return Arrays.stream(method.getDeclaredAnnotations())
                            .filter(a -> customQualifier.containsKey(a.annotationType()))
                            .allMatch(a -> ((MVCInstallation.QualifierFilter<A>) customQualifier.get(a.annotationType())).checkIsPass(e, patternClicked, (A) a));
                })
                .map(Map.Entry::getValue)
                .findFirst();
        if (targetMethod.isEmpty()) return false;
        Method m = targetMethod.get();
        try {
            var redirect = middleWareManager.intercept(m);
            if (returnTypeManager.handleReturnResult(BukkitView.class, redirect)) return true;
            Object[] results = parseManager.getMethodParameters(m, e);
            Object returnType = m.invoke(uiController, results);
            return returnTypeManager.handleReturnResult(m, returnType);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception) {
                throw (Exception) ex.getCause();
            }
            throw ex;
        }
    }

    protected abstract boolean slotTrigger(List<Integer> slots, E event);

    protected abstract RequestMapping toRequestMapping(A annotation);

}
