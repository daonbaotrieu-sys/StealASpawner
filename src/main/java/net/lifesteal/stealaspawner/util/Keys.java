package net.lifesteal.stealaspawner.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central registry of the NamespacedKeys StealASpawner stamps onto item
 * PersistentDataContainers. Must be initialised once in onEnable() before
 * anything else touches these fields.
 */
public final class Keys {

    private Keys() {
    }

    public static NamespacedKey PLUGIN_TAG;   // byte, 1 = "this ItemStack is a StealASpawner spawner"
    public static NamespacedKey ENTITY_TYPE;  // string, EntityType#name()
    public static NamespacedKey LEVEL_SPEED;  // integer
    public static NamespacedKey LEVEL_YIELD;  // integer
    public static NamespacedKey LEVEL_CAPACITY; // integer

    public static void init(Plugin plugin) {
        PLUGIN_TAG = new NamespacedKey(plugin, "sas_tag");
        ENTITY_TYPE = new NamespacedKey(plugin, "sas_entity_type");
        LEVEL_SPEED = new NamespacedKey(plugin, "sas_level_speed");
        LEVEL_YIELD = new NamespacedKey(plugin, "sas_level_yield");
        LEVEL_CAPACITY = new NamespacedKey(plugin, "sas_level_capacity");
    }
}
