package how.to.use.in.external;

import com.ericlam.mc.eldgui.view.UIContext;
import com.ericlam.mc.eldgui.view.View;

public class UserView implements View<User> {

    @Override
    public void renderView(User model, UIContext context) {
        System.out.println("show user info of " + model.username);
        System.out.println(model);
    }


}
