package com.ericlam.mc.eldgui.demo.login;

import com.ericlam.mc.eldgui.component.factory.ButtonFactory;
import com.ericlam.mc.eldgui.view.UIContext;
import com.ericlam.mc.eldgui.view.View;
import com.ericlam.mc.eldgui.view.ViewDescriptor;
import org.bukkit.Material;

@ViewDescriptor(
        name = "你的個人資料: ${playerName} (${username})",
        rows = 1,
        patterns = "AAAABAAAC",
        cancelMove = {'A', 'B', 'C'}
)
public class UserProfileView implements View<UserProfile> {

    @Override
    public void renderView(UserProfile model, UIContext context) {
        var btn = context.factory(ButtonFactory.class);
        context.pattern('B')
                .components(
                        btn.icon(Material.PAPER)
                                .title("&a你的個人資料")
                                .lore(
                                        "&e賬戶ID: &b" + model.username,
                                        "&e賬戶名稱: &b" + model.playerName,
                                        "&e是否管理員: &b" + model.isAdmin
                                )
                                .create()
                )
                .and()
                .pattern('C')
                .components(
                        btn.icon(Material.DIAMOND_PICKAXE)
                                .title("&b返回主界面")
                                .create()
                );

    }
}
