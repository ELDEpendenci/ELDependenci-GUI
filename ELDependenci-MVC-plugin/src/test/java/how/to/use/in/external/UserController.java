package how.to.use.in.external;

import com.ericlam.mc.eldgui.controller.ItemAttribute;
import com.ericlam.mc.eldgui.controller.UIController;
import com.ericlam.mc.eldgui.event.ClickMapping;
import com.ericlam.mc.eldgui.event.RequestMapping;
import com.ericlam.mc.eldgui.view.BukkitView;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@UIController("user")
public class UserController {

    @Inject
    private UserService userService;

    public BukkitView<?, ?> index() {
        List<User> users = userService.findAll();
        return new BukkitView<>(UserListView.class, users);
    }


    @RequestMapping(
            view = UserListView.class,
            event = InventoryClickEvent.class,
            pattern = 'A'
    )
    // or
    @ClickMapping(
            view = UserListView.class,
            pattern = 'A'
    )
    public BukkitView<?, ?> checkUserInfo(@ItemAttribute("id") String id, Player player) {
        Optional<User> user = userService.findById(id);
        if (user.isPresent()) {
            return new BukkitView<>(UserView.class, user.get());
        } else {
            player.sendMessage("user not exist.");
            return index();
        }
    }


}
