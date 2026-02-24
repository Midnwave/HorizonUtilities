package com.blockforge.horizonutilities.jobs.quests.objectives;

import com.blockforge.horizonutilities.events.JobExploreEvent;
import me.pikamug.quests.Quests;
import me.pikamug.quests.quests.Quest;
import me.pikamug.quests.module.BukkitCustomObjective;
import me.pikamug.quests.player.Quester;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;

import java.util.Map;

/**
 * PikaMug/Quests custom objective that tracks chunk exploration via the Jobs system.
 * Quest admins reference this as "Job: Explore Chunks" in their quest configs.
 */
public class JobExploreObjective extends BukkitCustomObjective {

    public JobExploreObjective() {
        setName("Job: Explore Chunks");
        setAuthor("HorizonUtilities");
        setDisplay("Explore %count% new chunks");
        setShowCount(true);
    }

    @EventHandler
    public void onJobExplore(JobExploreEvent event) {
        Quests qp = (Quests) Bukkit.getServer().getPluginManager().getPlugin("Quests");
        if (qp == null) return;
        Quester quester = qp.getQuester(event.getPlayer().getUniqueId());
        if (quester == null) return;
        for (Quest quest : quester.getCurrentQuests().keySet()) {
            incrementObjective(event.getPlayer().getUniqueId(), this, quest, 1);
        }
    }
}
