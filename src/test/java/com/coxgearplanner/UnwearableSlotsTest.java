package com.coxgearplanner;

import java.util.EnumMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The base outfit must not claim slots its own weapon rules out.
 *
 * A style's resolved picks name an item for every slot, including a shield
 * behind a two-handed weapon and ammunition for a bow that fires none. The
 * damage calculation ignores those, so the times were never wrong — but
 * everything reporting what is worn read them as equipped, which is how the
 * advice called a mage's book "already worn" while the equipped list showed
 * an empty shield slot, and how every style section claimed dragon arrows
 * were on next to a bow of faerdhinen.
 */
public class UnwearableSlotsTest
{
	private static final int BOFA = 25865;
	private static final int FANG = 26219;
	private static final int TBOW = 20997;

	private static Map<GearSlot, SetupBuilder.Pick> outfit(String weapon, int id, boolean twoHanded)
	{
		Map<GearSlot, SetupBuilder.Pick> picks = new EnumMap<>(GearSlot.class);
		ItemOption option = twoHanded
			? ItemOption.twoHanded(weapon, id)
			: ItemOption.of(weapon, id);
		picks.put(GearSlot.WEAPON, new SetupBuilder.Pick(option, ItemSource.BANK, id, 1));
		picks.put(GearSlot.SHIELD, new SetupBuilder.Pick(
			ItemOption.of("Mage's book", 6889), ItemSource.BANK, 6889, 1));
		picks.put(GearSlot.AMMO, new SetupBuilder.Pick(
			ItemOption.of("Dragon arrow", 11212), ItemSource.BANK, 11212, 500));
		return picks;
	}

	@Test
	public void aTwoHandedWeaponEmptiesTheShieldSlot()
	{
		Map<GearSlot, SetupBuilder.Pick> base = outfit("Bow of faerdhinen (c)", BOFA, true);
		SwitchAdvisor.emptyUnwearableSlots(base);

		assertNull("nothing can be worn in the shield slot behind a 2h weapon",
			base.get(GearSlot.SHIELD));
	}

	/** A bow of faerdhinen fires no ammunition, so that slot is empty too. */
	@Test
	public void aWeaponThatUsesNoAmmoEmptiesTheAmmoSlot()
	{
		Map<GearSlot, SetupBuilder.Pick> base = outfit("Bow of faerdhinen (c)", BOFA, true);
		SwitchAdvisor.emptyUnwearableSlots(base);

		assertNull(base.get(GearSlot.AMMO));
	}

	/** A bow that does take arrows keeps them. */
	@Test
	public void aBowThatNeedsArrowsKeepsThem()
	{
		Map<GearSlot, SetupBuilder.Pick> base = outfit("Twisted bow", TBOW, true);
		SwitchAdvisor.emptyUnwearableSlots(base);

		assertNotNull("a twisted bow needs its arrows", base.get(GearSlot.AMMO));
		assertNull(base.get(GearSlot.SHIELD));
	}

	/** A one-handed weapon leaves the shield slot alone. */
	@Test
	public void aOneHandedWeaponKeepsItsShield()
	{
		Map<GearSlot, SetupBuilder.Pick> base = outfit("Osmumten's fang", FANG, false);
		SwitchAdvisor.emptyUnwearableSlots(base);

		assertNotNull(base.get(GearSlot.SHIELD));
	}

	@Test
	public void anOutfitWithNoWeaponIsLeftAlone()
	{
		Map<GearSlot, SetupBuilder.Pick> base = new EnumMap<>(GearSlot.class);
		base.put(GearSlot.SHIELD, new SetupBuilder.Pick(
			ItemOption.of("Mage's book", 6889), ItemSource.BANK, 6889, 1));

		SwitchAdvisor.emptyUnwearableSlots(base);

		assertNotNull(base.get(GearSlot.SHIELD));
	}
}
