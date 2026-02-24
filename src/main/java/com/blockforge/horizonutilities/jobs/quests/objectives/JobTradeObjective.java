package com.blockforge.horizonutilities.jobs.quests.objectives;

import com.blockforge.horizonutilities.events.JobTradeEvent;
import me.pikamug.quests.Quests;
import me.pikamug.quests.quests.Quest;
import me.pikamug.quests.module.BukkitCustomObjective;
import me.pikamug.quests.player.Quester;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;

/**
 * PikaMug/Quests custom objective that tracks player-to-player trades via the Jobs system.
 * Quest admins reference this as "Job: Player Trade" in their quest configs.
 */
public class JobTradeObjective extends BukkitCustomObjective {

    public JobTradeObjective() {
        setName("Job: Player Trade");
        setAuthor("HorizonUtilities");
        setDisplay("Complete %count% player trade(s)");
        setShowCount(true);
    }

    @EventHandler
    public void onJobTrade(JobTradeEvent event) {
        Quests qp = (Quests) Bukkit.getServer().getPluginManager().getPlugin("Quests");
        if (qp == null) return;
        Quester quester = qp.getQuester(event.getPlayer().getUniqueId());
        if (quester == null) return;
        for (Quest quest : quester.getCurrentQuests().keySet()) {
            incrementObjective(event.getPlayer().getUniqueId(), this, quest, 1);
        }
    }
}
