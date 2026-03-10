package com.blockforge.horizonutilities.quests.rewards;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines rewards for a quest template. Values are base amounts before tier scaling.
 */
public class RewardDefinition {

    private final double money;
    private final double jobXp;
    private final int xpLevels;
    private final int gems;
    private final List<ItemReward> items;

    public RewardDefinition(double money, double jobXp, int xpLevels, int gems, List<ItemReward> items) {
        this.money = money;
        this.jobXp = jobXp;
        this.xpLevels = xpLevels;
        this.gems = gems;
        this.items = items;
    }

    public double getMoney()          { return money; }
    public double getJobXp()          { return jobXp; }
    public int getXpLevels()          { return xpLevels; }
    public int getGems()              { return gems; }
    public List<ItemReward> getItems(){ return items; }

    public boolean hasAnyReward() {
        return money > 0 || jobXp > 0 || xpLevels > 0 || gems > 0 || !items.isEmpty();
    }

    /**
     * Create a scaled copy of this reward definition.
     */
    public RewardDefinition scaled(double rewardMultiplier) {
        List<ItemReward> scaledItems = new ArrayList<>();
        for (ItemReward item : items) {
            scaledItems.add(new ItemReward(item.material(), Math.max(1, (int)(item.amount() * rewardMultiplier))));
        }
        return new RewardDefinition(
                money * rewardMultiplier,
                jobXp * rewardMultiplier,
                Math.max(1, (int)(xpLevels * rewardMultiplier)),
                Math.max(0, (int)(gems * rewardMultiplier)),
                scaledItems
        );
    }

    public static RewardDefinition fromConfig(ConfigurationSection section) {
        if (section == null) return new RewardDefinition(0, 0, 0, 0, List.of());
        double money = section.getDouble("money", 0);
        double jobXp = section.getDouble("job-xp", 0);
        int xpLevels = section.getInt("xp-levels", 0);
        int gems = section.getInt("gems", 0);

        List<ItemReward> items = new ArrayList<>();
        List<?> itemList = section.getList("items");
        if (itemList != null) {
            for (Object obj : itemList) {
                if (obj instanceof ConfigurationSection itemSec) {
                    String matStr = itemSec.getString("material", "STONE");
                    int amount = itemSec.getInt("amount", 1);
                    try {
                        Material mat = Material.valueOf(matStr.toUpperCase());
                        items.add(new ItemReward(mat, amount));
                    } catch (IllegalArgumentException ignored) {}
                } else if (obj instanceof java.util.Map<?, ?> map) {
                    Object matObj = map.get("material");
                    String matStr = matObj != null ? String.valueOf(matObj) : "STONE";
                    int amount = map.containsKey("amount") ? ((Number) map.get("amount")).intValue() : 1;
                    try {
                        Material mat = Material.valueOf(matStr.toUpperCase());
                        items.add(new ItemReward(mat, amount));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return new RewardDefinition(money, jobXp, xpLevels, gems, items);
    }

    public record ItemReward(Material material, int amount) {}
}
