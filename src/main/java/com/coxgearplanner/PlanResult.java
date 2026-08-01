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
	 * Room times once the switch budget is applied. Only rooms that lose
	 * something appear; everything else is already correct in {@link #times}.
	 */
	private Map<RoomTimeEstimator.RoomTime, Double> budgetedSeconds =
		Collections.emptyMap();

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

	void setBudgetedSeconds(Map<RoomTimeEstimator.RoomTime, Double> budgetedSeconds)
	{
		this.budgetedSeconds = budgetedSeconds;
	}

	/**
	 * How long this room really takes with the switches the plan carries. The
	 * estimator's own figure assumes every switch is available, so a room that
	 * gave one up has to be read from here or the plan quotes a time for gear
	 * it just told you to leave in the bank.
	 */
	public double secondsFor(RoomTimeEstimator.RoomTime time)
	{
		Double budgeted = budgetedSeconds.get(time);
		return budgeted != null ? budgeted : time.getSeconds();
	}

	/** Seconds lost across the raid to switches that did not fit the budget. */
	public double secondsLostToBudget()
	{
		double lost = 0;
		for (RoomTimeEstimator.RoomTime time : times)
		{
			if (time.isFeasible())
			{
				lost += secondsFor(time) - time.getSeconds();
			}
		}
		return lost;
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
