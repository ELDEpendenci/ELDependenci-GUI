package com.ericlam.mc.eldgui.manager;

import com.ericlam.mc.eldgui.lifecycle.PostConstruct;
import com.ericlam.mc.eldgui.lifecycle.PostUpdateView;
import com.ericlam.mc.eldgui.lifecycle.PreDestroy;
import com.ericlam.mc.eldgui.lifecycle.PreDestroyView;
import com.ericlam.mc.eldgui.view.View;
import org.apache.commons.lang3.Validate;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class LifeCycleManager {

    private final Object controller;
    private final MethodParseManager methodParseManager;

    private final Map<Class<? extends Annotation>, Method> lifeCycleMap = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, LifeCycleFilter<? extends Annotation>> viewLifeCycleFilterMap = new HashMap<>();
    private final Map<Class<? extends Annotation>, List<Method>> viewLifeCycleMap = new ConcurrentHashMap<>();

    public LifeCycleManager(Object controller, MethodParseManager methodParseManager) {
        this.controller = controller;
        this.methodParseManager = methodParseManager;

        this.loadLifeCycle(PostConstruct.class);
        this.loadLifeCycle(PreDestroy.class);

        this.loadViewLifeCycle(PostUpdateView.class, (a, v) -> a.value() == v);
        this.loadViewLifeCycle(PreDestroyView.class, (a, v) -> a.value() == v);
    }


    private <A extends Annotation> void loadLifeCycle(Class<A> lifeCycle) {
        List<Method> methods = Arrays.stream(controller.getClass().getMethods()).filter(m -> m.isAnnotationPresent(lifeCycle)).collect(Collectors.toList());
        if (methods.size() > 1)
            throw new IllegalStateException(lifeCycle.getName() + " lifecycle must at most once in each controller.");
        if (!methods.isEmpty()) {
            this.lifeCycleMap.put(lifeCycle, verifyReturnType(methods.get(0)));
        }
    }

    private <A extends Annotation> void loadViewLifeCycle(Class<A> lifeCycle, LifeCycleFilter<A> filter) {
        this.viewLifeCycleFilterMap.put(lifeCycle, filter);
        List<Method> methods = Arrays.stream(controller.getClass().getMethods()).filter(m -> m.isAnnotationPresent(lifeCycle)).collect(Collectors.toList());
        if (methods.isEmpty()) return;
        this.viewLifeCycleMap.put(lifeCycle, methods);
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation, E extends View<?>> void onViewLifeCycle(Class<A> lifeCycle, Class<E> viewCls) {
        Optional.ofNullable(this.viewLifeCycleMap.get(lifeCycle)).flatMap(methods -> methods.parallelStream().filter(m -> {
            A anno = m.getAnnotation(lifeCycle);
            return Optional
                    .ofNullable((LifeCycleFilter<A>) this.viewLifeCycleFilterMap.get(lifeCycle))
                    .map(f -> f.isPass(anno, viewCls))
                    .orElse(true);
        }).findAny()).ifPresent(m -> {
            try {
                m.invoke(controller, methodParseManager.getMethodParameters(m, null));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        });
    }

    public <A extends Annotation> void onLifeCycle(Class<A> lifeCycle) {
        Optional.ofNullable(this.lifeCycleMap.get(lifeCycle)).ifPresent(m -> {
            try {
                m.invoke(controller, methodParseManager.getMethodParameters(m, null));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        });
    }

    private Method verifyReturnType(Method m) {
        Validate.isTrue(m.getReturnType().equals(void.class), "return type of the LifeCycle method must be void");
        return m;
    }

    @FunctionalInterface
    public interface LifeCycleFilter<A extends Annotation> {

        boolean isPass(A annotation, Class<? extends View<?>> view);

    }

}
