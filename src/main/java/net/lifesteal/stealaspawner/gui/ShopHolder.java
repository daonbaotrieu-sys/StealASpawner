package net.lifesteal.stealaspawner.gui;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class ShopHolder implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, EntityType> slotToType;

    public ShopHolder(Map<Integer, EntityType> slotToType) {
        this.slotToType = slotToType;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public EntityType typeAt(int slot) {
        return slotToType.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
