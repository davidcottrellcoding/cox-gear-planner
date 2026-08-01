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
	public void theLargeMuttadileCanBeMeleedOnceItEmerges()
	{
		// It is submerged only while the small one is alive; after that it is
		// fully attackable, so melee must not be excluded. Ranged is still
		// preferred because its stomp hits everyone in melee range.
		MonsterProfile large = profile(CoxRoom.MUTTADILES, 1);
		assertTrue("melee works once it comes out of the water",
			large.getUsableStyles().contains(GearNeed.MELEE));
		assertTrue(large.getPreferredStyles().contains(GearNeed.RANGED));
		assertFalse("melee is possible but not preferred",
			large.getPreferredStyles().contains(GearNeed.MELEE));

		// The small one on land is meleeable too
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
	public void iceDemonFavoursMagicAndDemonbaneMelee()
	{
		// Both bypass its 67% damage reduction: fire spells via the elemental
		// weakness, demonbane weapons via their own carve-out. Ranged is the
		// one style with no way around the reduction.
		MonsterProfile demon = profile(CoxRoom.ICE_DEMON, 0);
		assertTrue(demon.getPreferredStyles().contains(GearNeed.MAGIC));
		assertTrue("demonbane melee is competitive here",
			demon.getPreferredStyles().contains(GearNeed.MELEE));
		assertFalse(demon.getPreferredStyles().contains(GearNeed.RANGED));
	}

	@Test
	public void guardiansArePickaxeOnly()
	{
		MonsterProfile guardian = profile(CoxRoom.GUARDIANS, 0);
		assertEquals(1, guardian.getUsableStyles().size());
		assertTrue(guardian.getUsableStyles().contains(GearNeed.MELEE));
		assertTrue(guardian.getStyleNote().contains("pickaxe"));

		// The restriction has to bind the WEAPON, not just the style —
		// otherwise the planner suggests the best melee weapon you own,
		// which cannot damage them at all.
		assertEquals(GearNeed.PICKAXE, guardian.getRequiredWeapon());
	}

	@Test
	public void onlyGuardiansRestrictTheWeapon()
	{
		for (CoxRoom room : CoxRoom.values())
		{
			for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(room))
			{
				GearNeed required = encounter.getProfile().getRequiredWeapon();
				if (room == CoxRoom.GUARDIANS)
				{
					assertEquals(GearNeed.PICKAXE, required);
				}
				else
				{
					assertEquals(room + " should not restrict the weapon", null, required);
				}
			}
		}
	}

	@Test
	public void aRestrictedWeaponClassIsAKnownUtility()
	{
		GearNeed required = profile(CoxRoom.GUARDIANS, 0).getRequiredWeapon();
		assertFalse("the restriction must resolve to real items",
			GearDatabase.utility(required).isEmpty());
		assertFalse("a pickaxe is not a combat style", required.isCombatStyle());
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
	public void olmClawsAreImmuneOutsideTheirStyleButTheHeadIsNot()
	{
		// The claws take damage from exactly one style each; only the head is
		// open to all three. The vanguards stay unconstrained — each is soft to
		// one style but attackable with any.
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.OLM))
		{
			MonsterProfile profile = encounter.getProfile();
			if (profile.getName().contains("claw"))
			{
				assertEquals(profile.getName() + " allows exactly one style",
					1, profile.getUsableStyles().size());
			}
			else
			{
				assertEquals(3, profile.getUsableStyles().size());
			}
		}
		for (RoomMonsters.Encounter encounter : RoomMonsters.getAll(CoxRoom.VANGUARDS))
		{
			assertEquals(3, encounter.getProfile().getUsableStyles().size());
		}
	}
}
