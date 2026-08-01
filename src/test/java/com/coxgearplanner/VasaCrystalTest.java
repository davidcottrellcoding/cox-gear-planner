package com.coxgearplanner;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Vasa's room needs two different things: ranged for Vasa himself, and a stab
 * weapon for the glowing crystals he siphons from. The crystals are immune to
 * ranged, take 66% less from magic and resist crush and slash, so the ranged
 * setup that kills Vasa cannot touch them.
 */
public class VasaCrystalTest
{
	@Test
	public void theRoomDemandsAStabWeaponAsWellAsRanged()
	{
		assertTrue("Vasa himself is a ranged target",
			CoxRoom.VASA.getNeeds().contains(GearNeed.RANGED));
		assertTrue("the crystals need stab",
			CoxRoom.VASA.getNeeds().contains(GearNeed.STAB_WEAPON));
	}

	@Test
	public void onlyVasaNeedsAStabWeapon()
	{
		for (CoxRoom room : CoxRoom.values())
		{
			if (room != CoxRoom.VASA)
			{
				assertFalse(room + " should not demand a stab weapon",
					room.getNeeds().contains(GearNeed.STAB_WEAPON));
			}
		}
	}

	@Test
	public void theStabListIsRealStabWeapons()
	{
		java.util.List<ItemOption> stab = GearDatabase.utility(GearNeed.STAB_WEAPON);
		assertFalse(stab.isEmpty());
		// Best-first: the fang leads, and every entry is a genuine stab weapon
		assertEquals("Osmumten's fang", stab.get(0).getName());

		java.util.Set<String> names = new java.util.HashSet<>();
		for (ItemOption option : stab)
		{
			names.add(option.getName());
			for (int id : option.getItemIds())
			{
				assertTrue("id must be positive", id > 0);
			}
		}
		assertTrue(names.contains("Ghrazi rapier"));
		assertTrue(names.contains("Abyssal dagger"));
		// A crush weapon has no business here
		assertFalse(names.contains("Inquisitor's mace"));
		assertFalse(names.contains("Dragon warhammer"));
	}

	@Test
	public void aStabWeaponIsAUtilityNotACombatStyle()
	{
		// It rides in the utility list, so if your melee weapon already stabs
		// the loadout dedupes it and it costs no extra inventory slot
		assertFalse(GearNeed.STAB_WEAPON.isCombatStyle());
	}
}
