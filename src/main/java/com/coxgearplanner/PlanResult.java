package com.coxgearplanner;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Everything the panel renders for one "Suggest gear setup" press. */
public class PlanResult
{
	private final List<SetupBuilder.Section> sections;
	private final List<RoomTimeEstimator.RoomTime> times;
	private final List<SwitchAdvisor.Advice> switchAdvice;
	private final GearNeed primaryStyle;
	private final RaidLoadoutBuilder.RaidLoadout loadout;
	private final PlanExplanation explanation;
	/** Plain-text rendering of this plan, for sharing. */
	private String exportText = "";
	/**
	 * What the raid would take with every switch carried. {@link #times} is the
	 * real figure — computed against the kit this plan actually packs — and the
	 * gap between the two is what the switch budget costs you.
	 */
	private List<RoomTimeEstimator.RoomTime> idealTimes = Collections.emptyList();

	PlanResult(List<SetupBuilder.Section> sections,
		List<RoomTimeEstimator.RoomTime> times,
		List<SwitchAdvisor.Advice> switchAdvice,
		GearNeed primaryStyle,
		RaidLoadoutBuilder.RaidLoadout loadout,
		PlanExplanation explanation)
	{
		this.sections = sections;
		this.times = times;
		this.switchAdvice = switchAdvice;
		this.primaryStyle = primaryStyle;
		this.loadout = loadout;
		this.explanation = explanation;
	}

	void setIdealTimes(List<RoomTimeEstimator.RoomTime> idealTimes)
	{
		this.idealTimes = idealTimes;
	}

	/** How long this room takes with the gear this plan actually brings. */
	public double secondsFor(RoomTimeEstimator.RoomTime time)
	{
		return time.getSeconds();
	}

	/**
	 * Seconds the switch budget costs you: the gap between this plan's kit and
	 * carrying every switch you own. Zero when the budget was never binding.
	 */
	public double secondsLostToBudget()
	{
		return feasibleTotal(times) - feasibleTotal(idealTimes);
	}

	private static double feasibleTotal(List<RoomTimeEstimator.RoomTime> list)
	{
		double total = 0;
		for (RoomTimeEstimator.RoomTime time : list)
		{
			if (time.isFeasible())
			{
				total += time.getSeconds();
			}
		}
		return total;
	}

	void setExportText(String exportText)
	{
		this.exportText = exportText;
	}

	/** The whole plan as plain text, ready to paste to someone else. */
	public String getExportText()
	{
		return exportText;
	}

	/** Debug reasoning; null unless the debug panel is enabled. */
	public PlanExplanation getExplanation()
	{
		return explanation;
	}

	/** Single-inventory raid layout; null when no room has a feasible kill. */
	public RaidLoadoutBuilder.RaidLoadout getLoadout()
	{
		return loadout;
	}

	public List<SetupBuilder.Section> getSections()
	{
		return sections;
	}

	public List<RoomTimeEstimator.RoomTime> getTimes()
	{
		return times;
	}

	public List<SwitchAdvisor.Advice> getSwitchAdvice()
	{
		return switchAdvice;
	}

	/** The style worn as the base outfit; null when advice is empty. */
	public GearNeed getPrimaryStyle()
	{
		return primaryStyle;
	}
}
