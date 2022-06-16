package com.ericlam.mc.eldgui.event;

import com.ericlam.mc.eldgui.MVCInstallation;
import com.ericlam.mc.eldgui.manager.MethodParseManager;
import com.ericlam.mc.eldgui.manager.ReturnTypeManager;
import com.ericlam.mc.eldgui.middleware.MiddleWareManager;
import com.ericlam.mc.eldgui.view.View;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ELDGClickEventHandler extends ELDGEventHandler<ClickMapping, InventoryClickEvent> {

    public ELDGClickEventHandler(Object controller, MethodParseManager parseManager, ReturnTypeManager returnTypeManager, MiddleWareManager middleWareManager, Map<Class<? extends Annotation>, MVCInstallation.QualifierFilter<? extends Annotation>> customQualifier, Method[] declaredMethods) {
        super(controller, parseManager, returnTypeManager, middleWareManager, customQualifier, declaredMethods);
    }

    @Override
    protected Map<ClickMapping, Method> loadAllHandlers(Method[] controllerMethods) {
        return Arrays.stream(controllerMethods).parallel()
                .filter(m -> m.isAnnotationPresent(ClickMapping.class))
                .collect(Collectors.toMap(m -> m.getAnnotation(ClickMapping.class), m -> m));
    }


    /* handleComponentClick method filtered
    @Override
    public boolean onEventHandle(InventoryClickEvent e, Player player, ELDGView<?> eldgView) throws Exception{
        if (eldgView.getNativeInventory() != e.getClickedInventory()) return false;
        if (e.getSlotType() != InventoryType.SlotType.CONTAINER) return false;
        if (e.getCurrentItem() == null) return false;
        return super.onEventHandle(e, player, eldgView);
    }

     */

    @Override
    protected boolean slotTrigger(List<Integer> slots, InventoryClickEvent event) {
        return slots.contains(event.getSlot());
    }

    @Override
    protected RequestMapping toRequestMapping(ClickMapping annotation) {
        return new RequestMapping() {

            @Override
            public Class<? extends View<?>> view() {
                return annotation.view();
            }

            @Override
            public Class<? extends InventoryInteractEvent> event() {
                return InventoryClickEvent.class;
            }

            @Override
            public char pattern() {
                return annotation.pattern();
            }

            @Override
            public boolean ignoreCancelled() {
                return annotation.ignoreCancelled();
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RequestMapping.class;
            }

        };
    }
}
