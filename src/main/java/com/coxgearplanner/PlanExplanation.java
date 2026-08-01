package com.coxgearplanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable reasoning behind a plan: why each slot's item beat the
 * runner-up, why each room's weapon won, and what each carried switch is
 * actually worth. Populated only when the debug panel is enabled.
 */
public class PlanExplanation
{
	private final List<String> gearChoices = new ArrayList<>();
	private final List<String> weaponChoices = new ArrayList<>();
	private final List<String> switchChoices = new ArrayList<>();
	private final List<String> weaponPool = new ArrayList<>();

	void addWeaponPool(String line)
	{
		weaponPool.add(line);
	}

	/** Which weapons were found in your item pools, and which were not. */
	public List<String> getWeaponPool()
	{
		return weaponPool;
	}

	void addGearChoice(String line)
	{
		gearChoices.add(line);
	}

	void addWeaponChoice(String line)
	{
		weaponChoices.add(line);
	}

	void addSwitchChoice(String line)
	{
		switchChoices.add(line);
	}

	/** "Style slot: chosen (score) — beat runner-up (score)". */
	public List<String> getGearChoices()
	{
		return gearChoices;
	}

	/** "Room: winner at X dps — next best Y at Z dps". */
	public List<String> getWeaponChoices()
	{
		return weaponChoices;
	}

	/** "Slot: +Xs when added — carried/skipped vs threshold". */
	public List<String> getSwitchChoices()
	{
		return switchChoices;
	}

	public boolean isEmpty()
	{
		return gearChoices.isEmpty() && weaponChoices.isEmpty()
			&& switchChoices.isEmpty() && weaponPool.isEmpty();
	}
}
