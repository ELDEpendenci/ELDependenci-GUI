package com.ericlam.mc.eldgui.demo.login;

import com.ericlam.mc.eld.misc.DebugLogger;
import com.ericlam.mc.eld.services.LoggingService;
import com.ericlam.mc.eldgui.component.PasswordInputField;
import com.ericlam.mc.eldgui.component.factory.PasswordInputFactory;
import com.google.inject.Inject;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private final Map<String, String> userStorage = new ConcurrentHashMap<>();
    private final Map<UUID, LoginSession> sessionStorage = new ConcurrentHashMap<>();

    private final DebugLogger logger;

    @Inject
    public AuthService(LoggingService loggingService) {
        this.logger = loggingService.getLogger(getClass());
        // for demo only, so plain password is visible
        this.userStorage.put("admin1234", PasswordInputField.hash("admin1234", PasswordInputFactory.HashType.MD5));
        this.userStorage.put("lam4477", PasswordInputField.hash("lam4477", PasswordInputFactory.HashType.MD5));
    }

    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            logger.debugF("password or username is null");
            return false;
        }
        if (!this.userStorage.containsKey(username)) {
            logger.debugF("username not exist: %s", username);
            return false;
        }
        return this.userStorage.get(username).equals(password);
    }

    public boolean isAdmin(String username) {
        return username.equals("admin1234");
    }

    public boolean isAdmin(LoginSession content) {
        return this.isAdmin(content.username);
    }

    public void saveSession(UUID player, LoginSession content) {
        this.sessionStorage.put(player, content);
    }

    @Nullable
    public LoginSession getSession(UUID player) {
        return this.sessionStorage.get(player);
    }

    public void removeSession(UUID player) {
        this.sessionStorage.remove(player);
    }

}
