package com.ericlam.mc.eldgui;

import com.ericlam.mc.eldgui.config.ELDGLanguage;
import com.google.inject.Injector;
import org.bukkit.Bukkit;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public class ELDGInventoryService implements InventoryService {

    private final ELDGPlugin eldgPlugin = ELDGPlugin.getPlugin(ELDGPlugin.class);
    private final Map<String, ELDGDispatcher> uiDispatcherMap = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> uiControllerMap;

    @Inject
    public ELDGInventoryService(ELDGMVCInstallation installation) {
        this.uiControllerMap = installation.getControllerMap();
    }

    @Inject
    private ELDGLanguage language;
    @Inject
    private Injector injector;

    @Override
    public UIDispatcher getUIDispatcher(String name) throws UINotFoundException {
        if (uiDispatcherMap.containsKey(name)) return uiDispatcherMap.get(name);
        Class<?> controllerCls = Optional.ofNullable(uiControllerMap.get(name)).orElseThrow(() -> new UINotFoundException(MessageFormat.format(language.getLang().get("not-found"), name)));
        Object controller = injector.getInstance(controllerCls);
        ELDGDispatcher dispatcher = new ELDGDispatcher(controller, (s, player, ui) -> this.getUIDispatcher(ui).openFor(player));
        injector.injectMembers(dispatcher);
        Bukkit.getServer().getPluginManager().registerEvents(dispatcher, eldgPlugin);
        this.uiDispatcherMap.put(name, dispatcher);
        return dispatcher;
    }

    public synchronized void onClose() {
        this.uiDispatcherMap.values().forEach(ELDGDispatcher::onClose);
    }
}
