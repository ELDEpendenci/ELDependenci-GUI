package com.ericlam.mc.eldgui.demo;

import com.ericlam.mc.eld.annotations.GroupResource;
import com.ericlam.mc.eldgui.InventoryTemplate;

@GroupResource(
        folder = "templates",
        preloads = {"user", "user-list"}
)
public class DemoInventories extends InventoryTemplate {
}
