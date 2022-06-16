package com.ericlam.mc.eldgui.demo.middlewares;

import com.ericlam.mc.eld.misc.DebugLogger;
import com.ericlam.mc.eld.services.LoggingService;
import com.ericlam.mc.eldgui.demo.login.LoginSession;
import com.ericlam.mc.eldgui.demo.login.LoginView;
import com.ericlam.mc.eldgui.middleware.InterceptContext;
import com.ericlam.mc.eldgui.middleware.MiddleWare;
import com.ericlam.mc.eldgui.view.BukkitView;
import com.google.inject.Inject;

public class AuthenticateMiddleWare implements MiddleWare<RequireLogin> {

    private final DebugLogger logger;

    @Inject
    public AuthenticateMiddleWare(LoggingService loggingService) {
        this.logger = loggingService.getLogger(getClass());
    }

    @Override
    public void intercept(InterceptContext context, RequireLogin annotation) throws Exception {
        LoginSession session = context.getSession().getAttribute("session");
        if (session == null) {
            logger.debugF("player %s is not login in, redirecting to login page", context.getPlayer().getName());
            context.setRedirect(new BukkitView<>(LoginView.class));
        } else {
            logger.debugF("player %s is login in, continue", context.getPlayer().getName());
        }
    }
}
