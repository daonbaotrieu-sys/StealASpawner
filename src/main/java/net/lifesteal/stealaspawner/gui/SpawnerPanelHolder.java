package net.lifesteal.stealaspawner.gui;

import net.lifesteal.stealaspawner.data.SpawnerData;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Tags an open Inventory as "this is the upgrade panel for this specific
 * spawner" so {@link GuiListener} knows how to route clicks without
 * guessing from inventory titles.
 */
public final class SpawnerPanelHolder implements InventoryHolder {

    private final SpawnerData data;
    private Inventory inventory;

    public SpawnerPanelHolder(SpawnerData data) {
        this.data = data;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public SpawnerData data() {
        return data;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
