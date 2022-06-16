package com.ericlam.mc.eldgui.demo.middlewares;

import com.ericlam.mc.eld.misc.DebugLogger;
import com.ericlam.mc.eld.services.LoggingService;
import com.ericlam.mc.eldgui.demo.login.AuthService;
import com.ericlam.mc.eldgui.demo.login.LoginSession;
import com.ericlam.mc.eldgui.middleware.InterceptContext;
import com.ericlam.mc.eldgui.middleware.MiddleWare;
import com.ericlam.mc.eldgui.view.BukkitRedirectView;
import com.google.inject.Inject;

public class AuthorizeMiddleWare implements MiddleWare<RequireAdmin> {

    @Inject
    private AuthService authService;

    private final DebugLogger logger;

    @Inject
    public AuthorizeMiddleWare(LoggingService loggingService) {
        this.logger = loggingService.getLogger(getClass());
    }

    @Override
    public void intercept(InterceptContext context, RequireAdmin annotation) throws Exception {
        LoginSession session = context.getSession().getAttribute("session");
        logger.debugF("session is null: %s", session == null);
        if (session != null && !authService.isAdmin(session)) {
            logger.debugF("player %s is not admin, redirecting to error page", context.getPlayer().getName());
            context.getPlayer().sendMessage("you are not admin.");
            context.getSession().setAttribute("exception", new Exception("you are not admin."));
            context.getSession().setAttribute("from", "login");
            context.setRedirect(new BukkitRedirectView("error"));
        } else {
            logger.debugF("player %s is admin, continue", context.getPlayer().getName());
        }
    }

}
