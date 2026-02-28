# HorizonUtilities Permissions

All permission nodes used by the HorizonUtilities plugin. Default values indicate who has the permission by default (`true` = everyone, `op` = operators only, `false` = nobody, must be granted).

---

## Auction House

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.ah.use` | true | Access the auction house GUI |
| `horizonutilities.ah.list` | true | List items for auction |
| `horizonutilities.ah.buy` | true | Buy items from the auction house |
| `horizonutilities.ah.admin` | op | Admin commands (expire, remove, reload) |
| `horizonutilities.ah.bypass.fee` | false | Bypass listing fees |
| `horizonutilities.ah.bypass.tax` | false | Bypass auction sale tax |
| `horizonutilities.ah.bypass.cooldown` | false | Bypass listing cooldown |
| `horizonutilities.ah.listings.<N>` | — | Allow up to N active listings (1–100). Checked dynamically from highest to lowest. |

---

## Jobs

### General

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.jobs.use` | true | Access the jobs system |
| `horizonutilities.jobs.join` | true | Join a job |
| `horizonutilities.jobs.leave` | true | Leave a job |
| `horizonutilities.jobs.info` | true | View job info and rewards |
| `horizonutilities.jobs.stats` | true | View personal job stats |
| `horizonutilities.jobs.top` | true | View job leaderboards |
| `horizonutilities.jobs.quests` | true | View daily quests |
| `horizonutilities.jobs.prestige` | true | Prestige a maxed-out job |
| `horizonutilities.jobs.<jobId>.join` | — | Allow joining a specific job (overrides `jobs.join`) |

### Admin

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.jobs.admin` | op | All job admin commands (parent) |
| `horizonutilities.jobs.admin.setlevel` | op | Set a player's job level |
| `horizonutilities.jobs.admin.addxp` | op | Add XP to a player's job |
| `horizonutilities.jobs.admin.reset` | op | Reset a player's job data |
| `horizonutilities.jobs.admin.forcejoin` | op | Force a player into a job |
| `horizonutilities.jobs.admin.boost` | op | Set a global XP/income multiplier |
| `horizonutilities.jobs.admin.reload` | op | Reload jobs configuration |

### Earning Actions

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.jobs.earn.break` | true | Earn from breaking blocks |
| `horizonutilities.jobs.earn.place` | true | Earn from placing blocks |
| `horizonutilities.jobs.earn.kill` | true | Earn from killing mobs |
| `horizonutilities.jobs.earn.fish` | true | Earn from fishing |
| `horizonutilities.jobs.earn.craft` | true | Earn from crafting items |
| `horizonutilities.jobs.earn.smelt` | true | Earn from smelting items |
| `horizonutilities.jobs.earn.brew` | true | Earn from brewing potions |
| `horizonutilities.jobs.earn.enchant` | true | Earn from enchanting items |
| `horizonutilities.jobs.earn.explore` | true | Earn from chunk exploration |
| `horizonutilities.jobs.earn.farm` | true | Earn from harvesting crops |
| `horizonutilities.jobs.earn.trade` | true | Earn from villager trading |
| `horizonutilities.jobs.earn.tame` | true | Earn from taming animals |
| `horizonutilities.jobs.earn.shear` | true | Earn from shearing sheep |
| `horizonutilities.jobs.earn.milk` | true | Earn from milking cows |
| `horizonutilities.jobs.earn.repair` | true | Earn from repairing items |

### Tax

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.tax.exempt` | false | Exempt from GriefPrevention claim job taxes |

---

## Chat Games

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.chatgames.play` | true | Participate in chat games |
| `horizonutilities.chatgames.admin` | op | Admin commands (start, stop, reload) |
| `horizonutilities.chatgames.top` | true | View chat game leaderboard |

---

## Chat Placeholders

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.placeholder.item` | true | Use `[item]` placeholder to show held item in chat |
| `horizonutilities.placeholder.pos` | true | Use `[pos]` placeholder to show coordinates in chat |
| `horizonutilities.placeholder.health` | true | Use `[health]` placeholder to show health in chat |
| `horizonutilities.placeholder.balance` | true | Use `[balance]` placeholder to show balance in chat |
| `horizonutilities.placeholder.ping` | true | Use `[ping]` placeholder to show latency in chat |

---

## Chat Bubbles

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.chatbubbles.use` | true | Toggle personal floating chat bubbles on/off |

---

## Bounty System

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.bounty.use` | true | View active bounties |
| `horizonutilities.bounty.set` | true | Place a bounty on another player |
| `horizonutilities.bounty.anonymous` | true | Place bounties anonymously |
| `horizonutilities.bounty.admin` | op | Admin commands (remove, clear, reload) |

---

## Lottery

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.lottery.use` | true | Buy lottery tickets and view status |
| `horizonutilities.lottery.admin` | op | Admin commands (draw, cancel, reload) |

---

## Trade System

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.trade.use` | true | Send and accept trade requests |
| `horizonutilities.trade.bypass.blacklist` | false | Trade blacklisted items |

---

## Black Market

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.blackmarket.use` | true | Access the black market GUI |
| `horizonutilities.blackmarket.admin` | op | Admin commands (refresh, reload) |

---

## Admin Warps

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.warp.use` | true | Teleport to admin-set warps |
| `horizonutilities.warp.admin` | op | Create, delete, and manage admin warps |

---

## Spawn

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.spawn.use` | true | Teleport to the server spawn |
| `horizonutilities.spawn.admin` | op | Set the server spawn point |

---

## Player Warps

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.pwarp.use` | true | Browse and teleport to player warps |
| `horizonutilities.pwarp.set` | true | Create a player warp |
| `horizonutilities.pwarp.delete` | true | Delete your own player warps |
| `horizonutilities.pwarp.rate` | true | Rate other players' warps |
| `horizonutilities.pwarp.admin` | op | Admin commands (delete any warp, reload) |
| `horizonutilities.warps.5` | false | Allow up to 5 player warps |
| `horizonutilities.warps.10` | false | Allow up to 10 player warps |
| `horizonutilities.warps.unlimited` | false | Unlimited player warps |

Warp limit permissions are configurable in `player-warps.yml`. The values above are defaults.

---

## Combat System

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.combat.use` | true | View combat tag status |
| `horizonutilities.combat.admin` | op | Admin commands (tag, untag, reload) |
| `horizonutilities.combat.bypass` | false | Bypass combat tag restrictions (e.g. logging out, teleporting) |

---

## Custom Items

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.customitem.admin` | op | Give custom items to players |

---

## Tournaments

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.tournament.use` | true | View and participate in tournaments |
| `horizonutilities.tournament.admin` | op | Create, start, stop, and manage tournaments |

---

## Economy Administration

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.eco.admin` | op | Economy audit log and admin commands |

---

## Configuration

| Permission | Default | Description |
|---|---|---|
| `horizonutilities.config.admin` | op | Edit plugin configuration via the in-game dialog/GUI editor |
