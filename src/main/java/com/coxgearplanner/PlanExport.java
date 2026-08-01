package com.coxgearplanner;

import java.util.List;
import java.util.Set;

/**
 * Renders a finished plan as plain text you can paste to someone else.
 *
 * A reviewer cannot judge the loadout from the item list alone — whether a
 * switch was dropped for being low value or for hitting the switch cap, and
 * which stats and party size the times assume, all change the answer. So the
 * settings and the assumptions are included, not just the result.
 */
public final class PlanExport
{
	private PlanExport()
	{
	}

	public static String render(
		Set<CoxRoom> rooms,
		PlayerSnapshot player,
		CoxGearPlannerConfig config,
		PlanResult result)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("CoX Gear Planner v").append(CoxGearPlannerPlugin.VERSION).append(" — plan\n");
		sb.append("================================================\n\n");

		StringBuilder roomList = new StringBuilder();
		for (CoxRoom room : CoxRoom.values())
		{
			if (rooms.contains(room))
			{
				if (roomList.length() > 0)
				{
					roomList.append(", ");
				}
				roomList.append(room.getDisplayName());
			}
		}
		sb.append("ROOMS: ").append(roomList.length() == 0 ? "none" : roomList).append("\n\n");

		sb.append("STATS USED  att ").append(player.getAttack())
			.append(" / str ").append(player.getStrength())
			.append(" / rng ").append(player.getRanged())
			.append(" / mag ").append(player.getMagic())
			.append(" / mining ").append(player.getMining()).append("\n");
		sb.append("SETTINGS    party ").append(config.partySize())
			.append(", min switch ").append(config.minSwitchSeconds()).append("s")
			.append(config.totalSwapItems() > 0
				? ", total swap items " + config.totalSwapItems()
					+ " (offhands and utilities not counted)"
				: ", max items/switch "
					+ (config.maxSwitchItems() == 0 ? "no limit" : config.maxSwitchItems()))
			.append(config.olmFourTick() ? ", Olm forced 4-tick" : "")
			.append(config.assumeOverload() ? ", overload" : "")
			.append(config.assumeImbuedHeart() ? ", imbued heart" : "")
			.append(config.assumeElitePrayers() ? ", elite prayers" : "")
			.append(config.includeGroupStorage() ? ", group storage on" : ", group storage OFF")
			.append("\n\n");

		RaidLoadoutBuilder.RaidLoadout loadout = result.getLoadout();
		if (loadout == null)
		{
			sb.append("No feasible plan — no selected room has a usable weapon.\n");
			return sb.toString();
		}

		int worn = 0;
		for (SetupBuilder.Line line : loadout.getEquipped())
		{
			if (!line.isMissing() && line.getSource() != null)
			{
				worn++;
			}
		}
		sb.append("EQUIPPED (").append(worn).append("/11) — base outfit: ")
			.append(loadout.getPrimaryStyle().getDisplayName().toLowerCase()).append("\n");
		for (SetupBuilder.Line line : loadout.getEquipped())
		{
			sb.append("  ").append(pad(line.getLabel(), 8)).append(line.getItemName());
			if (line.getSource() != null)
			{
				sb.append("  [").append(line.getSource().getDisplayName()).append("]");
			}
			sb.append("\n");
		}

		sb.append("\nINVENTORY (").append(loadout.getUsedSlots()).append("/")
			.append(RaidLoadoutBuilder.INVENTORY_SIZE).append(" used by gear, ")
			.append(loadout.getFreeSlots()).append(" free for supplies)\n");
		int n = 0;
		for (RaidLoadoutBuilder.Entry entry : loadout.getInventory())
		{
			sb.append("  ");
			sb.append(entry.isMissing() ? "  -- " : pad((++n) + ".", 4));
			sb.append(entry.getName()).append("  (").append(entry.getNote()).append(")");
			if (entry.getSource() != null)
			{
				sb.append(" [").append(entry.getSource().getDisplayName()).append("]");
			}
			sb.append("\n");
		}

		List<RoomTimeEstimator.RoomTime> times = result.getTimes();
		if (!times.isEmpty())
		{
			sb.append("\nESTIMATED TIMES (your share of the damage)\n");
			double total = 0;
			for (RoomTimeEstimator.RoomTime time : times)
			{
				sb.append("  ").append(pad(time.getDisplayName(), 42));
				if (time.isFeasible())
				{
					double seconds = result.secondsFor(time);
					total += seconds;
					sb.append(formatSeconds(seconds)).append("  ").append(time.getDetail());
				}
				else
				{
					sb.append("--:--  ").append(time.getDetail());
				}
				sb.append("\n");
			}
			sb.append("  ").append(pad("TOTAL COMBAT", 42)).append(formatSeconds(total)).append("\n");
			double lost = result.secondsLostToBudget();
			if (lost > 0.5)
			{
				sb.append("  ").append(pad("  of which lost to the switch budget", 42))
					.append("+").append(formatSeconds(lost)).append("\n");
			}
		}

		List<SwitchAdvisor.Advice> advice = result.getSwitchAdvice();
		if (!advice.isEmpty())
		{
			sb.append("\nSWITCH DECISIONS\n");
			for (SwitchAdvisor.Advice a : advice)
			{
				String verdict = a.isAlreadyShared() ? "shared"
					: a.isWorthIt() ? "CARRY"
					: a.isOverLimit() ? "over limit"
					: "skip";
				sb.append("  ").append(pad(verdict, 11))
					.append(pad(a.getStyle().getDisplayName().toLowerCase()
						+ " " + a.getSlot().getDisplayName().toLowerCase(), 18))
					.append(pad(a.getItemName(), 26));
				if (a.isAlreadyShared())
				{
					// Already worn, so there is no decision - but the slot is
					// still worth something, and saying how much is the only
					// way to tell a slot doing nothing from one doing a lot.
					sb.append(String.format("%.1fs vs %s", a.getSecondsSaved(),
						a.getWearInstead() == null ? "an empty slot" : a.getWearInstead()));
				}
				else
				{
					sb.append(String.format("%.1fs", a.getSecondsSaved()));
					if (a.getWearInstead() != null && !a.isWorthIt())
					{
						sb.append("  (keep ").append(a.getWearInstead()).append(")");
					}
				}
				sb.append("\n");
			}
		}

		for (SetupBuilder.Section section : loadout.getStyleSections())
		{
			sb.append("\n").append(section.getTitle().toUpperCase()).append("\n");
			for (SetupBuilder.Line line : section.getLines())
			{
				sb.append("  ").append(pad(line.getLabel(), 8)).append(line.getItemName()).append("\n");
			}
		}

		sb.append("\n------------------------------------------------\n");
		sb.append("Times are expected kill time from DPS only — no movement,\n");
		sb.append("mechanics, phases or special attacks, and they assume the\n");
		sb.append("party splits damage evenly. Treat them as a way to rank\n");
		sb.append("gear, not as a prediction of the run.\n");
		return sb.toString();
	}

	private static String pad(String text, int width)
	{
		StringBuilder sb = new StringBuilder(text == null ? "" : text);
		while (sb.length() < width)
		{
			sb.append(' ');
		}
		return sb.toString();
	}

	private static String formatSeconds(double seconds)
	{
		int total = (int) Math.round(seconds);
		return String.format("%d:%02d", total / 60, total % 60);
	}
}
