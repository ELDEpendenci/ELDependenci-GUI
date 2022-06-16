package com.ericlam.mc.eldgui.component;

import com.ericlam.mc.eld.services.ItemStackService;
import com.ericlam.mc.eldgui.component.factory.PasswordInputFactory;
import com.ericlam.mc.eldgui.component.modifier.Clickable;
import com.ericlam.mc.eldgui.component.modifier.Listenable;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import org.apache.commons.codec.binary.Hex;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PasswordInputField extends AbstractComponent implements Listenable<AsyncChatEvent>, Clickable {

    private static final Map<PasswordInputFactory.HashType, String> HASH_TYPE_MAP = Map.of(
            PasswordInputFactory.HashType.MD5, "MD5",
            PasswordInputFactory.HashType.SHA_256, "SHA-256"
    );


    private final String showPasswordTxt;
    private final String hidePasswordTxt;
    private final String inputMessage;
    private final String invalidMessage;
    private final long maxWait;
    private final Pattern regex;
    private final PasswordInputFactory.HashType hashType;
    private final boolean disabled;
    private final char mask;

    private String plainText;
    private boolean showText = false;


    public PasswordInputField(AttributeController attributeController,
                              ItemStackService.ItemFactory itemFactory,
                              String showPasswordTxt,
                              String hidePasswordTxt,
                              String inputMessage,
                              String invalidMessage,
                              long maxWait,
                              Pattern regex,
                              PasswordInputFactory.HashType hashType,
                              boolean disabled,
                              char mask) {
        super(attributeController, itemFactory);
        this.showPasswordTxt = showPasswordTxt;
        this.hidePasswordTxt = hidePasswordTxt;
        this.inputMessage = inputMessage;
        this.invalidMessage = invalidMessage;
        this.maxWait = maxWait;
        this.regex = regex;
        this.hashType = hashType;
        this.disabled = disabled;
        this.mask = mask;

        this.plainText = attributeController.getAttribute(getItem(), AttributeController.VALUE_TAG);
        this.updateItem();
    }

    @Override
    public boolean shouldActivate(InventoryClickEvent e) {
        return e.getClick() != ClickType.MIDDLE;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.MIDDLE) return;
        this.showText = !this.showText;
        this.updateItem();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onListen(Player player) {
        player.sendMessage(inputMessage);
    }

    @Override
    public long getMaxWaitingTime() {
        return maxWait;
    }

    @Override
    public void callBack(AsyncChatEvent event) {
        String message = ((TextComponent) event.message()).content();
        if (!regex.matcher(message).find()) {
            event.getPlayer().sendMessage(invalidMessage);
            return;
        }
        this.plainText = message;
        final String value = hash(message, hashType);
        this.attributeController.setAttribute(getItem(), AttributeController.VALUE_TAG, value);
        this.updateItem();
    }

    @Override
    public Class<AsyncChatEvent> getEventClass() {
        return AsyncChatEvent.class;
    }


    private void updateItem() {
        itemFactory.lore(List.of(
                "&7-> &f" + (plainText == null ? "NONE" : (showText ? plainText : String.valueOf(mask).repeat(plainText.length()))),
                "&b中鍵以 " + (showText ? hidePasswordTxt : showPasswordTxt)
        ));
        this.updateInventory();
    }

    public static String hash(String password, PasswordInputFactory.HashType type) {
        try {
            var digest = MessageDigest.getInstance(HASH_TYPE_MAP.get(type));
            byte[] hashed = digest.digest(password.getBytes());
            return Hex.encodeHexString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
