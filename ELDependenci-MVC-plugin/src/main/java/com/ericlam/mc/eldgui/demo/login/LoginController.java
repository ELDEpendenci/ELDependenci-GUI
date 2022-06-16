package com.ericlam.mc.eldgui.demo.login;

import com.ericlam.mc.eldgui.UISession;
import com.ericlam.mc.eldgui.controller.MapAttribute;
import com.ericlam.mc.eldgui.controller.UIController;
import com.ericlam.mc.eldgui.demo.middlewares.RequireAdmin;
import com.ericlam.mc.eldgui.demo.middlewares.RequireLogin;
import com.ericlam.mc.eldgui.event.ClickMapping;
import com.ericlam.mc.eldgui.lifecycle.PostConstruct;
import com.ericlam.mc.eldgui.view.BukkitView;
import com.google.inject.Inject;
import org.bukkit.entity.Player;

import java.util.Map;

@UIController("login")
public class LoginController {

    @Inject
    private AuthService authService;

    @PostConstruct
    public void beforeEnterView(UISession session, Player player) {
        var s = authService.getSession(player.getUniqueId());
        if (s != null && session.getAttribute("session") == null) {
            session.setAttribute("session", s);
        }
    }

    @RequireLogin
    public BukkitView<?, ?> index() {
        return new BukkitView<>(MainView.class);
    }

    @ClickMapping(view = LoginView.class, pattern = 'E')
    public BukkitView<?, ?> login(UISession session, @MapAttribute('C') Map<String, Object> map, Player player) {
        var username = (String) map.get("username");
        var success = authService.authenticate(username, (String) map.get("password"));
        if (success) {
            player.sendMessage("login success!");
            var s = new LoginSession(username, player.getName());
            session.setAttribute("session", s);
            authService.saveSession(player.getUniqueId(), s);
            return new BukkitView<>(MainView.class);
        } else {
            player.sendMessage("login failed.");
            return new BukkitView<>(LoginFailedView.class);
        }
    }


    @ClickMapping(view = LoginFailedView.class, pattern = 'C')
    public BukkitView<?, ?> backToLogin() {
        return new BukkitView<>(LoginView.class);
    }

    @RequireLogin
    @ClickMapping(view = MainView.class, pattern = 'C')
    public BukkitView<?, ?> logout(UISession session, Player player) {
        session.pollAttribute("session");
        authService.removeSession(player.getUniqueId());
        player.sendMessage("登出成功。");
        return new BukkitView<>(LoginView.class);
    }

    @RequireAdmin
    @RequireLogin
    @ClickMapping(view = MainView.class, pattern = 'D')
    public BukkitView<?, ?> toAdminView() {
        return new BukkitView<>(AdminContentView.class);
    }

    @RequireLogin
    @ClickMapping(view = AdminContentView.class, pattern = 'C')
    public BukkitView<?, ?> backToMain() {
        return new BukkitView<>(MainView.class);
    }

    @RequireLogin
    @ClickMapping(view = UserProfileView.class, pattern = 'C')
    public BukkitView<?, ?> backToMain2() {
        return new BukkitView<>(MainView.class);
    }

    @RequireLogin
    @ClickMapping(view = MainView.class, pattern = 'E')
    public BukkitView<?, ?> goToProfileView(UISession session, Player player) {
        LoginSession s = session.getAttribute("session");
        if (s == null) throw new RuntimeException("session is null");
        var admin = authService.isAdmin(s);
        var profile = new UserProfile(s.username, player.getName(), admin);
        return new BukkitView<>(UserProfileView.class, profile);
    }


}
