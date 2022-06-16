package com.ericlam.mc.eldgui;

import com.ericlam.mc.eld.*;
import com.ericlam.mc.eldgui.component.factory.*;
import com.ericlam.mc.eldgui.config.ELDGConfig;
import com.ericlam.mc.eldgui.config.ELDGLanguage;
import com.ericlam.mc.eldgui.demo.DemoInventories;
import com.ericlam.mc.eldgui.demo.async.AsyncController;
import com.ericlam.mc.eldgui.demo.error.ErrorController;
import com.ericlam.mc.eldgui.demo.login.LoginController;
import com.ericlam.mc.eldgui.demo.middlewares.AuthenticateMiddleWare;
import com.ericlam.mc.eldgui.demo.middlewares.AuthorizeMiddleWare;
import com.ericlam.mc.eldgui.demo.middlewares.RequireAdmin;
import com.ericlam.mc.eldgui.demo.middlewares.RequireLogin;
import com.ericlam.mc.eldgui.demo.test.TestController;
import com.ericlam.mc.eldgui.demo.user.UserController;

@ELDBukkit(
        lifeCycle = ELDGLifeCycle.class,
        registry = ELDGRegistry.class
)
public final class ELDGPlugin extends ELDBukkitPlugin {

    private final ELDGMVCInstallation eldgmvcInstallation = new ELDGMVCInstallation(this);


    @Override
    public void bindServices(ServiceCollection serviceCollection) {
        serviceCollection.bindService(InventoryService.class, ELDGInventoryService.class);
        serviceCollection.addGroupConfiguration(DemoInventories.class);
        serviceCollection.addConfiguration(ELDGLanguage.class);
        serviceCollection.addConfiguration(ELDGConfig.class);


        AddonInstallation addonManager = serviceCollection.getInstallation(AddonInstallation.class);
        addonManager.customInstallation(MVCInstallation.class, eldgmvcInstallation);

        // register component factory
        eldgmvcInstallation.addComponentFactory(ButtonFactory.class, ELDGButtonFactory.class);
        eldgmvcInstallation.addComponentFactory(TextInputFactory.class, ELDGTextInputFactory.class);
        eldgmvcInstallation.addComponentFactory(NumInputFactory.class, ELDGNumInputFactory.class);
        eldgmvcInstallation.addComponentFactory(AnimatedButtonFactory.class, ELDGAnimatedButtonFactory.class);
        eldgmvcInstallation.addComponentFactory(CheckboxFactory.class, ELDGCheckboxFactory.class);
        eldgmvcInstallation.addComponentFactory(SelectionFactory.class, ELDGSelectionFactory.class);
        eldgmvcInstallation.addComponentFactory(RGBSelectorFactory.class, ELDGRGBSelectorFactory.class);
        eldgmvcInstallation.addComponentFactory(BukkitItemFactory.class, ELDGBukkitItemFactory.class);
        eldgmvcInstallation.addComponentFactory(DateSelectorFactory.class, ELDGDateSelectorFactory.class);
        eldgmvcInstallation.addComponentFactory(TimeSelectorFactory.class, ELDGTimeSelectorFactory.class);
        eldgmvcInstallation.addComponentFactory(PasswordInputFactory.class, ELDGPasswordInputFactory.class);

        // install module
        addonManager.installModule(new ELDGMVCModule(eldgmvcInstallation));
    }


    @Override
    protected void manageProvider(BukkitManagerProvider bukkitManagerProvider) {
        ELDGConfig config = bukkitManagerProvider.getConfigStorage().getConfigAs(ELDGConfig.class);


        // my demo register
        if (config.enableDemo) {
            eldgmvcInstallation.registerControllers(
                    UserController.class,
                    ErrorController.class,
                    TestController.class,
                    AsyncController.class,
                    LoginController.class
            );

            eldgmvcInstallation.registerMiddleWare(RequireLogin.class, AuthenticateMiddleWare.class);
            eldgmvcInstallation.registerMiddleWare(RequireAdmin.class, AuthorizeMiddleWare.class);
        }

    }
}
