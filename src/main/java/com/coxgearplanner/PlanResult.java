package com.coxgearplanner;

import java.util.List;

/** Everything the panel renders for one "Suggest gear setup" press. */
public class PlanResult
{
	private final List<SetupBuilder.Section> sections;
	private final List<RoomTimeEstimator.RoomTime> times;
	private final List<SwitchAdvisor.Advice> switchAdvice;
	private final GearNeed primaryStyle;

	PlanResult(List<SetupBuilder.Section> sections,
		List<RoomTimeEstimator.RoomTime> times,
		List<SwitchAdvisor.Advice> switchAdvice,
		GearNeed primaryStyle)
	{
		this.sections = sections;
		this.times = times;
		this.switchAdvice = switchAdvice;
		this.primaryStyle = primaryStyle;
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
