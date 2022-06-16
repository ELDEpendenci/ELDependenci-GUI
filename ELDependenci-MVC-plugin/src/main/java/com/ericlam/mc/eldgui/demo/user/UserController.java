package com.ericlam.mc.eldgui.demo.user;

import com.ericlam.mc.eldgui.UISession;
import com.ericlam.mc.eldgui.controller.ItemAttribute;
import com.ericlam.mc.eldgui.controller.ModelAttribute;
import com.ericlam.mc.eldgui.controller.UIController;
import com.ericlam.mc.eldgui.event.ClickMapping;
import com.ericlam.mc.eldgui.lifecycle.PostUpdateView;
import com.ericlam.mc.eldgui.lifecycle.PreDestroyView;
import com.ericlam.mc.eldgui.view.BukkitRedirectView;
import com.ericlam.mc.eldgui.view.BukkitView;
import org.bukkit.entity.Player;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@UIController("user")
public class UserController {

    @Inject
    private UserService userService;

    // index
    public BukkitView<?, ?> index() {
        List<String> users = userService.findAllUsernames();
        return new BukkitView<>(UserListView.class, users);
    }


    @ClickMapping(view = UserListView.class, pattern = 'L')
    public BukkitView<?, ?> onClickUser(@ItemAttribute("username") String username, UISession session) throws UserNotFoundException {
        Optional<User> user = userService.findById(username);
        if (user.isPresent()) {
            return new BukkitView<>(UserView.class, user.get());
        } else {
            // try using throw exception -> try using pass controller
            //return new BukkitView<>(UserNotFoundView.class, username);
            UserNotFoundException e = new UserNotFoundException(username);
            session.setAttribute("exception", e);
            session.setAttribute("from", "user");
            return new BukkitRedirectView("error");
        }
    }

    @ClickMapping(view = UserListView.class, pattern = 'A')
    public BukkitView<?, ?> onCreateUser() {
        return new BukkitView<>(UserUpdateView.class);
    }

    @ClickMapping(view = UserListView.class, pattern = 'M')
    public BukkitView<?, ?> onResetClick() {
        userService.reset();
        return index();
    }

    @ClickMapping(view = UserNotFoundView.class, pattern = 'A')
    public BukkitView<?, ?> backFromNotFound() {
        return index();
    }

    @ClickMapping(view = UserView.class, pattern = 'B')
    public BukkitView<?, ?> backFromUser() {
        return index();
    }

    @ClickMapping(view = UserView.class, pattern = 'C')
    public BukkitView<?, ?> deleteUser(@ItemAttribute("to-delete") String username, Player player) {
        userService.removeUser(username);
        player.sendMessage("Delete Success");
        return index();
    }

    @ClickMapping(view = UserView.class, pattern = 'D')
    public BukkitView<?, ?> editUser(@ItemAttribute("to-edit") String username) {
        Optional<User> user = userService.findById(username);
        if (user.isPresent()) {
            return new BukkitView<>(UserUpdateView.class, user.get());
        } else {
            return new BukkitView<>(UserNotFoundView.class, username);
        }
    }

    @ClickMapping(view = UserUpdateView.class, pattern = 'B')
    public BukkitView<?, ?> onSave(@ModelAttribute('A') User user, Player player) {
        player.sendMessage("Pre Saving: " + user.toString());
        userService.save(user);
        player.sendMessage("Save Success");
        return index();
    }


    @PostUpdateView(UserView.class)
    public void updateToUserView(Player player) {
        player.sendMessage("post update: user view");
    }

    @PreDestroyView(UserListView.class)
    public void preDestroyUserListView(Player player) {
        player.sendMessage("pre destroy: user list view");
    }

    public static class UserNotFoundException extends Exception {

        public UserNotFoundException(String username) {
            super("username not found: " + username);
            this.username = username;
        }

        private final String username;

        public String getUsername() {
            return username;
        }

    }
}
