package com.ericlam.mc.eldgui;

import com.ericlam.mc.eld.services.ConfigPoolService;
import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eld.services.ReflectionService;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ELDGDispatcher implements UIDispatcher, Listener {

    private static final Map<Player, UISession> uiSessionMap = new ConcurrentHashMap<>();
    private final Map<Player, ELDGUI> guiSessionMap = new ConcurrentHashMap<>();

    private final Object controller;
    private final ViewJumper goTo;

    @Inject
    private Injector injector;
    @Inject
    private ELDGMVCInstallation eldgmvcInstallation;
    @Inject
    private ConfigPoolService configPoolService;
    @Inject
    private ItemStackService itemStackService;
    @Inject
    private ReflectionService reflectionService;

    public ELDGDispatcher(
            Object controller,
            ViewJumper goTo
    ) {
        this.controller = controller;
        this.goTo = (session, player, ui) -> {
            uiSessionMap.put(player, session);
            goTo.onJump(session, player, ui);
        };
    }


    @Override
    public void openFor(Player player) {
        this.openFor(player, s -> {
        });
    }

    @Override
    public void openFor(Player player, Consumer<UISession> initSession) {

        UISession session = Optional.ofNullable(uiSessionMap.remove(player)).orElseGet(() -> {
            UISession newSession = new ELDGUISession();
            initSession.accept(newSession);
            return newSession;
        });

        ELDGUI eldgui = new ELDGUI(
                controller,
                injector,
                session,
                player,
                guiSessionMap::remove,
                goTo,
                eldgmvcInstallation,
                configPoolService,
                itemStackService,
                reflectionService
        );
        this.guiSessionMap.put(player, eldgui);
    }

    @Override
    public void openForGlobal(Player player) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public synchronized void onClose() {
        this.guiSessionMap.values().forEach(ELDGUI::destroy);
        HandlerList.unregisterAll(this);
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Optional.ofNullable(guiSessionMap.get((Player) e.getWhoClicked())).ifPresent(gui -> gui.onInventoryClick(e));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        Optional.ofNullable(guiSessionMap.get((Player) e.getWhoClicked())).ifPresent(gui -> gui.onInventoryDrag(e));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Optional.ofNullable(guiSessionMap.get((Player) e.getPlayer())).ifPresent(gui -> gui.onInventoryClose(e));
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        Optional.ofNullable(guiSessionMap.get((Player) e.getPlayer())).ifPresent(gui -> gui.onInventoryOpen(e));
    }

    @SuppressWarnings("unchecked")
    private final static class ELDGUISession implements UISession {

        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public <T> T getAttribute(String key) {
            return (T) attributes.get(key);
        }

        @Override
        public void setAttribute(String key, Object value) {
            this.attributes.put(key, value);
        }

        @Override
        public <T> T pollAttribute(String key) {
            return (T) this.attributes.remove(key);
        }
    }
}
