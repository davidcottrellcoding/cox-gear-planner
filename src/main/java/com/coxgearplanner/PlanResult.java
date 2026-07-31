package com.coxgearplanner;

import java.util.List;

/** Everything the panel renders for one "Suggest gear setup" press. */
public class PlanResult
{
	private final List<SetupBuilder.Section> sections;
	private final List<RoomTimeEstimator.RoomTime> times;
	private final List<SwitchAdvisor.Advice> switchAdvice;
	private final GearNeed primaryStyle;
	private final RaidLoadoutBuilder.RaidLoadout loadout;
	private final PlanExplanation explanation;

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
