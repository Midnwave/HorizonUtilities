package com.blockforge.horizonutilities.story;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Factory for all 25 Hollow Chronicle Stage 1 items.
 * Items are tagged with PDC for identification.
 * CraftEngine handles the textures/models — this class creates server-side ItemStacks
 * for code-driven giving, drops, and identification.
 */
public class StoryItemManager {

    private final HorizonUtilitiesPlugin plugin;
    private final NamespacedKey storyItemKey;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<String, ItemDef> items = new LinkedHashMap<>();

    public StoryItemManager(HorizonUtilitiesPlugin plugin) {
        this.plugin = plugin;
        this.storyItemKey = new NamespacedKey(plugin, "story_item");
        registerItems();
    }

    private void registerItems() {
        // Progression (4)
        reg("vareths_insignia", Material.PAPER, "<#8B0000>Vareth's Insignia",
            List.of("<!i><gray>A fractured emblem pried from", "<!i><gray>a fallen sentinel's grasp."));
        reg("ashen_stone", Material.PAPER, "<#A0522D>Ashen Stone",
            List.of("<!i><gray>A smoldering stone pulled from", "<!i><gray>the cold fireplace. It still hums."));
        reg("wardens_key", Material.PAPER, "<#4B0082>Warden's Key",
            List.of("<!i><gray>Forged from insignia and ash,", "<!i><gray>it remembers the sealed gate."));
        reg("soul_of_the_vigil", Material.PAPER, "<#00CED1>Soul of the Vigil",
            List.of("<!i><gray>A tear of light that resonates", "<!i><gray>with the crypt altar below."));

        // Completion Drops (5)
        reg("vareths_sigil", Material.PAPER, "<#800080>Vareth's Sigil",
            List.of("<!i><gray>A stage completion token,", "<!i><gray>proof the chronicle remembers you."));
        reg("rune_of_ruin", Material.PAPER, "<#FF4500>Rune of Ruin",
            List.of("<!i><gray>One of seven. The end", "<!i><gray>will ask for all of them."));
        reg("vigils_edge", Material.IRON_SWORD, "<#C0C0C0>Vigil's Edge",
            List.of("<!i><gray>The sword Vareth carried", "<!i><gray>through centuries of silence."));
        reg("wardens_cuirass", Material.IRON_CHESTPLATE, "<#C0C0C0>Warden's Cuirass",
            List.of("<!i><gray>Armor that bore the weight", "<!i><gray>of an eternal watch."));
        reg("tome_of_first_herald", Material.WRITTEN_BOOK, "<#DAA520>Tome of the First Herald",
            List.of("<!i><gray>Written by Ilyss in the", "<!i><gray>hours before the vigil began."));

        // Side Quest (2)
        reg("veilborn_compass", Material.PAPER, "<#00BFFF>Veilborn Compass",
            List.of("<!i><gray>Pulses softly near things", "<!i><gray>the world has tried to forget."));
        reg("evelins_record_book", Material.PAPER, "<#DEB887>Evelin's Record-Book",
            List.of("<!i><gray>A water-stained journal hidden", "<!i><gray>beneath a collapsed table."));

        // Exploration (8)
        reg("keep_guards_blade", Material.IRON_SWORD, "<#808080>Keep Guard's Blade",
            List.of("<!i><gray>Still sharp after all these years."));
        reg("hollows_ward", Material.PAPER, "<#9370DB>Hollow's Ward",
            List.of("<!i><gray>An offhand charm that eases", "<!i><gray>the sting of withering."));
        reg("vigil_shard", Material.PAPER, "<#E0E0E0>Vigil Shard",
            List.of("<!i><gray>A fragment of something whole.", "<!i><gray>Collect five to restore it."));
        reg("forgotten_coin", Material.PAPER, "<#DAA520>Forgotten Coin",
            List.of("<!i><gray>Old currency from a kingdom", "<!i><gray>that no longer has a name."));
        reg("veilborn_rations", Material.PAPER, "<#D2B48C>Veilborn Rations",
            List.of("<!i><gray>Wrapped bread preserved by", "<!i><gray>means no baker would recognize."));
        reg("cracked_veilborn_sigil", Material.PAPER, "<#556B2F>Cracked Veilborn Sigil",
            List.of("<!i><gray>Broken, but not worthless.", "<!i><gray>A future stage may need this."));
        reg("veilborn_field_notes", Material.WRITTEN_BOOK, "<#696969>Veilborn Field Notes",
            List.of("<!i><gray>Hasty observations from a", "<!i><gray>soldier who never returned."));
        reg("miras_last_rose", Material.WITHER_ROSE, "<#FF69B4>Mira's Last Rose",
            List.of("<!i><gray>A wither rose that somehow", "<!i><gray>still holds a blush of pink."));

        // Craftable (4)
        reg("ashen_dust", Material.PAPER, "<#A0522D>Ashen Dust",
            List.of("<!i><gray>Ground from the Ashen Stone.", "<!i><gray>Useful as a crafting ingredient."));
        reg("vigil_crystal", Material.PAPER, "<#87CEEB>Vigil Crystal",
            List.of("<!i><gray>Five shards made whole.", "<!i><gray>Grants sight in the dark."));
        reg("veilborn_torch", Material.PAPER, "<#FF8C00>Veilborn Torch",
            List.of("<!i><gray>Burns with soul fire and", "<!i><gray>casts a wider light."));
        reg("hollow_ward_charm", Material.PAPER, "<#9370DB>Hollow Ward Charm",
            List.of("<!i><gray>Crafted protection against", "<!i><gray>the withering touch."));

        // Special (2)
        reg("stone_eye_emblem", Material.PAPER, "<#708090>Stone Eye Emblem",
            List.of("<!i><gray>Chiseled from the keep wall.", "<!i><gray>Its purpose spans stages."));
        reg("vigils_tear", Material.PAPER, "<#00CED1>Vigil's Tear",
            List.of("<!i><gray>Drawn from the deepest pool.", "<!i><gray>A hint of what Stage 2 holds."));
    }

    private void reg(String id, Material mat, String name, List<String> lore) {
        items.put(id, new ItemDef(id, mat, name, lore));
    }

    /**
     * Creates an ItemStack for the given story item ID.
     */
    public ItemStack createItem(String id) {
        return createItem(id, 1);
    }

    public ItemStack createItem(String id, int amount) {
        ItemDef def = items.get(id);
        if (def == null) return null;

        ItemStack stack = new ItemStack(def.material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MINI.deserialize(def.name).decoration(TextDecoration.ITALIC, false));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : def.lore) {
            loreComponents.add(MINI.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);

        // PDC tag for identification
        meta.getPersistentDataContainer().set(storyItemKey, PersistentDataType.STRING, id);

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Gets the story item ID from an ItemStack, or null if it isn't a story item.
     */
    public String getItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(storyItemKey, PersistentDataType.STRING);
    }

    /**
     * Checks if an ItemStack is a specific story item.
     */
    public boolean isItem(ItemStack stack, String id) {
        return id.equals(getItemId(stack));
    }

    /**
     * Returns all registered item IDs.
     */
    public List<String> getAllItemIds() {
        return List.copyOf(items.keySet());
    }

    public NamespacedKey getStoryItemKey() {
        return storyItemKey;
    }

    private record ItemDef(String id, Material material, String name, List<String> lore) {}
}
