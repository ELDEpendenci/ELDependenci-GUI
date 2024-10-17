package com.ericlam.mc.eldgui.middleware;

import com.ericlam.mc.eld.services.ReflectionService;
import com.ericlam.mc.eldgui.ELDGMVCInstallation;
import com.ericlam.mc.eldgui.UISession;
import com.ericlam.mc.eldgui.view.BukkitView;
import com.google.inject.Injector;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MiddleWareManager {
    private static final Map<Class<? extends Annotation>, MiddleWare<? extends Annotation>> middleWareMap = new ConcurrentHashMap<>();
    private final Annotation[] controllerAnnotations;


    private final ReflectionService reflectionCacheManager;
    private final Player player;
    private final UISession session;

    public MiddleWareManager(
            ReflectionService reflectionCacheManager,
            ELDGMVCInstallation installation,
            Injector injector,
            Class<?> controllerCls,
            Player player,
            UISession session
    ) {

        this.reflectionCacheManager = reflectionCacheManager;
        this.player = player;
        this.session = session;

        installation.getMiddleWares()
                .entrySet()
                .stream()
                .filter(en -> middleWareMap.containsKey(en.getKey()))
                .forEach(en -> {
                    var anno = en.getKey();
                    var middleWareClass = en.getValue();
                    MiddleWare<? extends Annotation> middleWare = injector.getInstance(middleWareClass);
                    middleWareMap.put(anno, middleWare);
                });

        this.controllerAnnotations = reflectionCacheManager.getDeclaredAnnotations(controllerCls);
    }

    @Nullable
    public BukkitView<?, ?> intercept(Method method) throws Exception {
        var context = new ELDGInterceptContext(player, session);
        var annotations = reflectionCacheManager.getDeclaredAnnotations(method);
        var totalAnnotations = (Annotation[]) ArrayUtils.addAll(controllerAnnotations, annotations);
        for (Annotation annotation : totalAnnotations) {
            if (!middleWareMap.containsKey(annotation.annotationType())) continue;
            invokeMiddleWare(annotation.annotationType(), annotation, context);
            if (context.getView() != null) {
                return context.getView();
            }
        }
        return context.getView();
    }


    @SuppressWarnings("unchecked")
    private <A extends Annotation> void invokeMiddleWare(Class<A> type, Annotation annotation, ELDGInterceptContext context) throws Exception {
        var anno = type.cast(annotation);
        var middleWare = (MiddleWare<A>) middleWareMap.get(type);
        middleWare.intercept(context, anno);
    }


}
