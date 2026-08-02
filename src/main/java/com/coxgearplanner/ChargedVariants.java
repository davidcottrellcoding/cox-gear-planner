package com.coxgearplanner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps uncharged / inactive / broken items onto the charged item they become.
 *
 * Owning an uncharged scythe still means you own a scythe — you just have to
 * charge it before the raid. Without this the stats-based picker would score
 * the uncharged item on its (near-worthless) uncharged bonuses and discard it,
 * so a bank full of uncharged gear looked like a bank full of nothing.
 *
 * Ids verified against runelite-api gameval ItemID constants.
 */
public final class ChargedVariants
{
	private static final Map<Integer, Integer> TO_CHARGED = new HashMap<>();

	/**
	 * Same-stats variants — ornament kits and alternate imbue sources — mapped
	 * onto the id the planner's tables know. Purely cosmetic: no charging
	 * needed and no stat difference, so unlike {@link #TO_CHARGED} these never
	 * raise the CHARGE IT FIRST flag.
	 */
	private static final Map<Integer, Integer> SAME_STATS = new HashMap<>();

	private ChargedVariants()
	{
	}

	private static void map(int uncharged, int charged)
	{
		TO_CHARGED.put(uncharged, charged);
	}

	private static void same(int variant, int base)
	{
		SAME_STATS.put(variant, base);
	}

	static
	{
		// Ornament kits — identical stats, purely cosmetic
		same(28688, 12926); // toxic blowpipe (ornament, loaded)
		same(28687, 12924); // toxic blowpipe (ornament, empty) -> then the charged map
		same(19720, 12002); // occult necklace (or)
		same(20366, 19553); // amulet of torture (or)
		same(22249, 19547); // necklace of anguish (or)
		same(23444, 19544); // tormented bracelet (or)
		same(12436, 6585);  // amulet of fury (or)
		same(19722, 12954); // dragon defender (t)

		// Alternate imbues — Soul Wars and the PvP Arena mint their own ids
		// with effects identical to the Nightmare Zone imbue
		same(25258, 11770); // seers ring (i), soul wars
		same(26767, 11770); // seers ring (i), pvp arena
		same(25260, 11771); // archers ring (i), soul wars
		same(26768, 11771); // archers ring (i), pvp arena
		same(25262, 11772); // warrior ring (i), soul wars
		same(26769, 11772); // warrior ring (i), pvp arena
		same(25264, 11773); // berserker ring (i), soul wars
		same(26770, 11773); // berserker ring (i), pvp arena
		same(25252, 13202); // ring of the gods (i), soul wars
		same(26764, 13202); // ring of the gods (i), pvp arena
		same(25193, 21752); // granite ring (i), soul wars
		same(26685, 21752); // granite ring (i), pvp arena
	}

	/** Same-stats variant (ornament kit, alternate imbue) -> the known id. */
	public static int sameStats(int itemId)
	{
		return SAME_STATS.getOrDefault(itemId, itemId);
	}

	static
	{
		// Forms whose name does not follow the _UNCHARGED/_INACTIVE pattern,
		// so the generated table below cannot find them.
		map(12924, 12926); // toxic blowpipe (empty) -> loaded
		map(30373, 30374); // blazing blowpipe (empty) -> loaded
		map(11907, 11905); // trident of the seas (full)
		map(22290, 22288); // trident of the seas (e), uncharged
		map(22294, 22292); // trident of the swamp (e), uncharged
		map(4212, 23983);  // crystal bow, pre-rework
		map(13091, 23987); // crystal halberd, pre-rework
		map(28826, 28951); // dizana's quiver (broken)
		map(28828, 28955); // dizana's quiver, infinite (broken)
		map(27676, 27679); // accursed sceptre (autocast recolour)

		// Generated from runelite-api gameval ItemID: every constant ending in
		// _UNCHARGED / _INACTIVE / _BROKEN / _EMPTY / _DEGRADED matched to its
		// base (or _CHARGED / _LOADED / _FULL) counterpart. Non-equipment
		// entries are harmless: only equipable items reach the gear scan.
		map(2957, 2958); // druid_pouch_empty
		map(3697, 3696); // viking_draugen_talisman_uncharged
		map(3805, 3803); // viking_tankard_empty
		map(4197, 4198); // fenk_head_empty
		map(4252, 4251); // ectophial_empty
		map(4614, 4613); // spinning_plate_broken
		map(4860, 4708); // barrows_ahrim_head_broken
		map(4866, 4710); // barrows_ahrim_weapon_broken
		map(4872, 4712); // barrows_ahrim_body_broken
		map(4878, 4714); // barrows_ahrim_legs_broken
		map(4884, 4716); // barrows_dharok_head_broken
		map(4890, 4718); // barrows_dharok_weapon_broken
		map(4896, 4720); // barrows_dharok_body_broken
		map(4902, 4722); // barrows_dharok_legs_broken
		map(4908, 4724); // barrows_guthan_head_broken
		map(4914, 4726); // barrows_guthan_weapon_broken
		map(4920, 4728); // barrows_guthan_body_broken
		map(4926, 4730); // barrows_guthan_legs_broken
		map(4932, 4732); // barrows_karil_head_broken
		map(4938, 4734); // barrows_karil_weapon_broken
		map(4944, 4736); // barrows_karil_body_broken
		map(4950, 4738); // barrows_karil_legs_broken
		map(4956, 4745); // barrows_torag_head_broken
		map(4962, 4747); // barrows_torag_weapon_broken
		map(4968, 4749); // barrows_torag_body_broken
		map(4974, 4751); // barrows_torag_legs_broken
		map(4980, 4753); // barrows_verac_head_broken
		map(4986, 4755); // barrows_verac_weapon_broken
		map(4992, 4757); // barrows_verac_body_broken
		map(4998, 4759); // barrows_verac_legs_broken
		map(5519, 5518); // scrying_orb_empty
		map(5600, 5599); // rd_tin_of_crap_empty
		map(6081, 6082); // mourning_paint_gun_broken
		map(9085, 9087); // lunar_moonclan_liminal_vial_empty
		map(9524, 9522); // aluft_batta_tin_empty
		map(10486, 10487); // anma_chicken_sack_empty
		map(10546, 10542); // barbassault_vial_04_empty
		map(10984, 10983); // dorgesh_powerstation_cog_broken
		map(10986, 10985); // dorgesh_powerstation_fuse_broken
		map(10988, 10987); // dorgesh_powerstation_meter_broken
		map(10990, 10989); // dorgesh_powerstation_battery_broken
		map(10992, 10991); // dorgesh_powerstation_lever_broken
		map(10994, 10993); // dorgesh_powerstation_powerbox_broken
		map(11151, 11154); // dream_vial_empty
		map(11284, 11283); // dragonfire_shield_uncharged
		map(11908, 11905); // tots_uncharged
		map(12809, 12808); // blessed_saradomin_sword_degraded
		map(12853, 12851); // damned_amulet_degraded
		map(12897, 12898); // antisanta_coalbox_empty
		map(12900, 12899); // toxic_tots_uncharged
		map(13183, 13185); // easter15_blaster_empty
		map(13242, 13241); // infernal_axe_empty
		map(13244, 13243); // infernal_pickaxe_empty
		map(13300, 13301); // hw15_jar_empty
		map(13347, 13351); // xmas15_tears_vial_empty
		map(13392, 13393); // xeric_talisman_empty
		map(20445, 6570); // tzhaar_cape_fire_broken
		map(20447, 13329); // skillcape_max_firecape_broken
		map(20449, 8844); // bronze_parryingdagger_broken
		map(20451, 8845); // iron_parryingdagger_broken
		map(20453, 8846); // steel_parryingdagger_broken
		map(20455, 8847); // black_parryingdagger_broken
		map(20457, 8848); // mithril_parryingdagger_broken
		map(20459, 8849); // adamant_parryingdagger_broken
		map(20461, 8850); // rune_parryingdagger_broken
		map(20463, 12954); // dragon_parryingdagger_broken
		map(20465, 8839); // pest_void_knight_top_broken
		map(20467, 13072); // elite_void_knight_top_broken
		map(20469, 8840); // pest_void_knight_robes_broken
		map(20471, 13073); // elite_void_knight_robes_broken
		map(20473, 8841); // pest_void_knight_mace_broken
		map(20475, 8842); // pest_void_knight_gloves_broken
		map(20477, 11663); // game_pest_mage_helm_broken
		map(20479, 11664); // game_pest_archer_helm_broken
		map(20481, 11665); // game_pest_melee_helm_broken
		map(20483, 4508); // castlewars_sword_3_broken
		map(20485, 4509); // castlewars_armour_body_3_broken
		map(20487, 4510); // castlewars_armour_legs_3_broken
		map(20489, 4511); // castlewars_med_helm_3_broken
		map(20491, 4512); // castlewars_shield_3_broken
		map(20493, 11895); // castlewars_armour_skirt_3_broken
		map(20495, 11896); // castlewars_mage_top_broken
		map(20497, 11897); // castlewars_mage_legs_broken
		map(20499, 11898); // castlewars_mage_hat_broken
		map(20501, 11899); // castlewars_range_top_broken
		map(20503, 11900); // castlewars_range_legs_broken
		map(20505, 11901); // castlewars_range_quiver_broken
		map(20507, 10548); // barbassault_penance_fighter_hat_broken
		map(20509, 10550); // barbassault_penance_ranger_hat_broken
		map(20511, 10547); // barbassault_penance_healer_hat_broken
		map(20513, 10551); // barbassault_penance_fighter_torso_broken
		map(20515, 10555); // barbassault_penance_ranger_legs_broken
		map(20537, 12637); // castlewars_saradomin_halo_broken
		map(20539, 12638); // castlewars_zamorak_halo_broken
		map(20541, 12639); // castlewars_guthix_halo_broken
		map(20716, 20714); // tome_of_fire_uncharged
		map(21033, 21031); // infernal_harpoon_empty
		map(21287, 21295); // infernal_cape_broken
		map(21289, 21285); // skillcape_max_infernalcape_broken
		map(21387, 21389); // bookofscrolls_empty
		map(21634, 21633); // wyvern_shield_uncharged
		map(21817, 21816); // wild_cave_bracelet_uncharged
		map(21873, 21874); // xmas17_santa_sack_empty
		map(21914, 22109); // avas_assembler_broken
		map(21916, 21898); // skillcape_max_assembler_broken
		map(22003, 22002); // dragonfire_ward_uncharged
		map(22290, 22288); // tots_i_uncharged
		map(22294, 22292); // toxic_tots_i_uncharged
		map(22368, 22370); // nature_staff_uncharged
		map(22441, 22322); // infernal_defender_broken
		map(22481, 22323); // sanguinesti_staff_uncharged
		map(22486, 22325); // scythe_of_vitur_uncharged
		map(22542, 22545); // wild_cave_chainmace_uncharged
		map(22547, 22550); // wild_cave_bow_uncharged
		map(22552, 22555); // wild_cave_sceptre_uncharged
		map(22679, 22682); // hw18_cauldron_empty
		map(23675, 23673); // crystal_axe_inactive
		map(23682, 23680); // crystal_pickaxe_inactive
		map(23764, 23762); // crystal_harpoon_inactive
		map(23973, 23971); // crystal_helmet_inactive
		map(23977, 23975); // crystal_chestplate_inactive
		map(23981, 23979); // crystal_platelegs_inactive
		map(23985, 23983); // crystal_bow_inactive
		map(23989, 23987); // crystal_halberd_inactive
		map(23993, 23991); // crystal_shield_inactive
		map(23997, 23995); // blade_of_saeldor_inactive
		map(24147, 24192); // armadyl_halo_broken
		map(24149, 24195); // bandos_halo_broken
		map(24151, 24198); // seren_halo_broken
		map(24153, 24201); // zaros_halo_broken
		map(24155, 24204); // brassica_halo_broken
		map(24236, 21791); // ma2_saradomin_cape_broken
		map(24238, 21776); // skillcape_max_saradomin2_broken
		map(24240, 21793); // ma2_guthix_cape_broken
		map(24242, 21784); // skillcape_max_guthix2_broken
		map(24244, 21795); // ma2_zamorak_cape_broken
		map(24246, 21780); // skillcape_max_zamorak2_broken
		map(24435, 24436); // xmas19_flour_pot_empty
		map(24531, 10549); // barbassault_penance_runner_hat_broken
		map(24619, 24617); // bh_vestas_longsword_inactive
		map(24735, 24736); // ring_of_endurance_uncharged
		map(25155, 25171); // castlewars_boots_3_broken
		map(25157, 25174); // castlewars_full_helm_3_broken
		map(25367, 25059); // trailblazer_harpoon_empty
		map(25369, 25063); // trailblazer_pickaxe_empty
		map(25371, 25066); // trailblazer_axe_empty
		map(25576, 25574); // tome_of_water_uncharged
		map(25625, 25641); // barronite_mace_broken
		map(25633, 25644); // imcando_hammer_broken
		map(25862, 25865); // bow_of_faerdhinen_inactive
		map(25989, 25987); // tumekens_heka_uncharged
		map(27277, 27275); // tumekens_shadow_uncharged
		map(27359, 27374); // avas_assembler_masori_broken
		map(27361, 27363); // skillcape_max_assembler_masori_broken
		map(27612, 27610); // venator_bow_uncharged
		map(27652, 27655); // wild_cave_webweaver_uncharged
		map(27657, 27660); // wild_cave_ursine_uncharged
		map(27662, 27665); // wild_cave_accursed_uncharged
		map(27902, 27900); // vestas_spear_bh_inactive
		map(27906, 27904); // vestas_longsword_bh_inactive
		map(27910, 27908); // statius_warhammer_bh_inactive
		map(27914, 27912); // morrigans_thrownaxe_bh_inactive
		map(27918, 27916); // morrigans_javelin_bh_inactive
		map(27922, 27920); // zuriels_staff_bh_inactive
		map(27925, 27831); // bh_vestas_chainbody_inactive
		map(27928, 27832); // bh_vestas_plateskirt_inactive
		map(27931, 27833); // bh_statius_full_helm_inactive
		map(27934, 27834); // bh_statius_platebody_inactive
		map(27937, 27835); // bh_statius_platelegs_inactive
		map(27940, 27836); // bh_morrigans_coif_inactive
		map(27943, 27837); // bh_morrigans_leather_body_inactive
		map(27946, 27838); // bh_morrigans_leather_chaps_inactive
		map(27949, 27839); // bh_zuriels_hood_inactive
		map(27952, 27840); // bh_zuriels_robe_top_inactive
		map(27955, 27841); // bh_zuriels_robe_bottom_inactive
		map(27965, 27842); // bh_vestas_chainbody_corrupt_inactive
		map(27968, 27843); // bh_vestas_plateskirt_corrupt_inactive
		map(27971, 27844); // bh_statius_full_helm_corrupt_inactive
		map(27974, 27845); // bh_statius_platebody_corrupt_inactive
		map(27977, 27846); // bh_statius_platelegs_corrupt_inactive
		map(27980, 27847); // bh_morrigans_coif_corrupt_inactive
		map(27983, 27848); // bh_morrigans_leather_body_corrupt_inactive
		map(27986, 27849); // bh_morrigans_leather_chaps_corrupt_inactive
		map(27989, 27850); // bh_zuriels_hood_corrupt_inactive
		map(27992, 27851); // bh_zuriels_robe_top_corrupt_inactive
		map(27995, 27852); // bh_zuriels_robe_bottom_corrupt_inactive
		map(28223, 28220); // crystal_axe_2h_inactive
		map(28238, 28260); // ancient_sceptre_blood_broken
		map(28240, 28264); // ancient_sceptre_smoke_broken
		map(28242, 28262); // ancient_sceptre_ice_broken
		map(28244, 28266); // ancient_sceptre_shadow_broken
		map(28329, 28327); // ring_of_shadows_uncharged
		map(28583, 28585); // warped_sceptre_uncharged
		map(28804, 28805); // dov_mist_bottle_empty
		map(28813, 28810); // zombie_axe_broken
		map(28826, 28951); // dizanas_quiver_broken
		map(28828, 28955); // dizanas_quiver_infinite_broken
		map(28830, 28902); // skillcape_max_dizanas_broken
		map(28919, 28922); // tonalztics_of_ralos_uncharged
		map(28947, 28951); // dizanas_quiver_uncharged
		map(29031, 29004); // eclipse_moon_chestplate_degraded
		map(29033, 29007); // eclipse_moon_tassets_degraded
		map(29035, 29010); // eclipse_moon_helm_degraded
		map(29037, 29013); // frost_moon_chestplate_degraded
		map(29039, 29016); // frost_moon_tassets_degraded
		map(29041, 29019); // frost_moon_helm_degraded
		map(29043, 29022); // blood_moon_chestplate_degraded
		map(29045, 29025); // blood_moon_tassets_degraded
		map(29047, 29028); // blood_moon_helm_degraded
		map(29049, 29004); // eclipse_moon_chestplate_broken
		map(29052, 29007); // eclipse_moon_tassets_broken
		map(29055, 29010); // eclipse_moon_helm_broken
		map(29058, 29013); // frost_moon_chestplate_broken
		map(29061, 29016); // frost_moon_tassets_broken
		map(29064, 29019); // frost_moon_helm_broken
		map(29067, 29022); // blood_moon_chestplate_broken
		map(29070, 29025); // blood_moon_tassets_broken
		map(29073, 29028); // blood_moon_helm_broken
		map(29892, 29893); // pendant_of_ates_empty
		map(29988, 29990); // amulet_of_chemistry_imbued_uncharged
		map(30066, 30064); // tome_of_earth_uncharged
		map(30305, 19675); // arclight_inactive
		map(30324, 30321); // zombie_helmet_broken
		map(30343, 30342); // trailblazer_reloaded_harpoon_empty
		map(30346, 30345); // trailblazer_reloaded_pickaxe_empty
		map(30348, 30347); // trailblazer_reloaded_axe_empty
		map(30392, 30390); // tangled_lizard_uncharged
		map(30436, 30434); // venator_bow_ornament_uncharged
		map(30543, 30445); // barrows_ahrim_head_ornament_broken
		map(30545, 30447); // barrows_ahrim_body_ornament_broken
		map(30547, 30449); // barrows_ahrim_legs_ornament_broken
		map(30574, 30568); // barrows_ahrim_weapon_ornament_broken
		map(30637, 30638); // giantsoul_amulet_uncharged
		map(30942, 30941); // vmq4_janus_purse_empty
		map(31115, 31113); // eye_of_ayak_uncharged
		map(31243, 31241); // horn_of_plenty_uncharged
		map(31577, 31575); // camphor_blowpipe_empty
		map(31581, 31579); // ironwood_blowpipe_empty
		map(31585, 31583); // rosewood_blowpipe_empty
		map(31809, 31810); // sailing_charting_weather_station_empty
		map(31989, 31992); // sailing_boat_bottle_empty
		map(32398, 32399); // sailors_amulet_empty
		map(33074, 33077); // sailing_facility_bottle_empty
		map(33103, 33104); // cowbell_amulet_empty
		map(33241, 33239); // league_flask_of_fervour_empty
		map(33257, 33255); // lithic_sceptre_uncharged
		map(33464, 24185); // game_pest_melee_helm_trouver_broken
		map(33468, 24183); // game_pest_mage_helm_trouver_broken
		map(33472, 24184); // game_pest_archer_helm_trouver_broken
		map(33476, 24177); // pest_void_knight_top_trouver_broken
		map(33480, 24178); // elite_void_knight_top_trouver_broken
		map(33484, 24179); // pest_void_knight_robes_trouver_broken
		map(33488, 24180); // elite_void_knight_robes_trouver_broken
		map(33492, 24182); // pest_void_knight_gloves_trouver_broken
		map(33496, 24133); // skillcape_max_infernalcape_trouver_broken
		map(33500, 24224); // infernal_cape_trouver_broken
		map(33504, 28473); // ancient_sceptre_blood_trouver_broken
		map(33508, 28474); // ancient_sceptre_ice_trouver_broken
		map(33512, 28475); // ancient_sceptre_smoke_trouver_broken
		map(33516, 28476); // ancient_sceptre_shadow_trouver_broken
		map(33520, 24175); // barbassault_penance_fighter_torso_trouver_broken
		map(33528, 28957); // dizanas_quiver_infinite_trouver_broken
		map(33532, 28906); // skillcape_max_dizanas_trouver_broken
		map(33827, 27626); // ancient_sceptre_trouver_broken
	}

	/**
	 * @return the charged equivalent of an item, or the id unchanged when it
	 * is already charged (or has no charged form).
	 */
	public static int canonical(int itemId)
	{
		// Chains exist (a broken form of an already-degraded item), so resolve
		// transitively; the hop limit also guards against a bad cyclic entry.
		int current = itemId;
		for (int hops = 0; hops < 4; hops++)
		{
			Integer next = TO_CHARGED.get(current);
			if (next == null || next == current)
			{
				break;
			}
			current = next;
		}
		return current;
	}

	/** True when this id is an uncharged/inactive/broken form of something else. */
	public static boolean needsCharging(int itemId)
	{
		return TO_CHARGED.containsKey(itemId);
	}

	static Map<Integer, Integer> all()
	{
		return Collections.unmodifiableMap(TO_CHARGED);
	}
}
