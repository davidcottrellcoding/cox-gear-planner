package com.coxgearplanner;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Positional and mechanical style constraints — things DPS alone cannot see.
 * A hard constraint is expressed by leaving a style out of usableStyles; a
 * mechanical preference (safespotting and the like) by prefers().
 */
public class RoomStyleConstraintTest
{
	private static MonsterProfile profile(CoxRoom room, int index)
	{
		return RoomMonsters.getAll(room).get(index).getProfile();
	}

	@Test
	public void tightropePlatformsCannotBeMeleedAtAll()
	{
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.TIGHTROPE))
		{
			Set<GearNeed> usable = encounter.getProfile().getUsableStyles();
			assertFalse("melee cannot reach the platforms",
				usable.contains(GearNeed.MELEE));
			assertTrue(usable.contains(GearNeed.RANGED));
			assertTrue(usable.contains(GearNeed.MAGIC));
		}
	}

	@Test
	public void theLargeMuttadileIsOutOfMeleeReach()
	{
		MonsterProfile large = profile(CoxRoom.MUTTADILES, 1);
		assertFalse("the large muttadile sits in the water",
			large.getUsableStyles().contains(GearNeed.MELEE));

		// The small one on land is meleeable
		assertTrue(profile(CoxRoom.MUTTADILES, 0).getUsableStyles().contains(GearNeed.MELEE));
	}

	@Test
	public void skeletalMysticsFavourRangedAndMagicForTheSafespot()
	{
		MonsterProfile mystic = profile(CoxRoom.MYSTICS, 0);
		Set<GearNeed> preferred = mystic.getPreferredStyles();
		assertTrue(preferred.contains(GearNeed.RANGED));
		assertTrue(preferred.contains(GearNeed.MAGIC));
		assertFalse("melee gives up the safespot", preferred.contains(GearNeed.MELEE));
		assertNotNull("the reason is shown to the user", mystic.getStyleNote());

		// Melee is still *possible*, just not preferred — so someone with only
		// a melee weapon still gets a plan rather than "infeasible"
		assertTrue(mystic.getUsableStyles().contains(GearNeed.MELEE));
	}

	@Test
	public void iceDemonFavoursMagicForTheFireSpells()
	{
		MonsterProfile demon = profile(CoxRoom.ICE_DEMON, 0);
		assertEquals(1, demon.getPreferredStyles().size());
		assertTrue(demon.getPreferredStyles().contains(GearNeed.MAGIC));
	}

	@Test
	public void guardiansArePickaxeOnly()
	{
		MonsterProfile guardian = profile(CoxRoom.GUARDIANS, 0);
		assertEquals(1, guardian.getUsableStyles().size());
		assertTrue(guardian.getUsableStyles().contains(GearNeed.MELEE));
		assertTrue(guardian.getStyleNote().contains("pickaxe"));
	}

	@Test
	public void tektonIsAMeleeFight()
	{
		assertTrue(profile(CoxRoom.TEKTON, 0).getPreferredStyles().contains(GearNeed.MELEE));
	}

	@Test
	public void everyPreferredStyleIsAlsoUsable()
	{
		// A preference for a style you are not allowed to use would silently
		// fall through to the usable set and confuse the room line
		for (CoxRoom room : CoxRoom.values())
		{
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				MonsterProfile profile = encounter.getProfile();
				for (GearNeed preferred : profile.getPreferredStyles())
				{
					assertTrue(room + " prefers " + preferred + " but cannot use it",
						profile.getUsableStyles().contains(preferred));
				}
				if (!profile.getPreferredStyles().isEmpty())
				{
					assertNotNull(room + " has a preference but no explanation",
						profile.getStyleNote());
				}
			}
		}
	}

	@Test
	public void olmAndVanguardsStayUnconstrained()
	{
		// Both rooms deliberately demand all three styles across their targets
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			assertEquals(3, encounter.getProfile().getUsableStyles().size());
			assertTrue(encounter.getProfile().getPreferredStyles().isEmpty());
		}
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.VANGUARDS))
		{
			assertEquals(3, encounter.getProfile().getUsableStyles().size());
		}
	}
}
