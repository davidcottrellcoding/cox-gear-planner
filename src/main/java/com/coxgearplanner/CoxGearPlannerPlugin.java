package com.coxgearplanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import com.google.inject.Provides;

@PluginDescriptor(
	name = "CoX Gear Planner",
	description = "Suggests a Chambers of Xeric gear setup from your bank and group storage based on the raid's room layout",
	tags = {"cox", "raids", "chambers", "xeric", "gear", "gim", "group", "loadout"}
)
public class CoxGearPlannerPlugin extends Plugin
{
	/** Shown in the panel title; keep in sync with build.gradle. */
	static final String VERSION = "1.25.1";

	// Item container ids. Raw values are used because the InventoryID API
	// has been migrated between RuneLite versions.
	private static final int CONTAINER_INVENTORY = 93;
	private static final int CONTAINER_EQUIPMENT = 94;
	private static final int CONTAINER_BANK = 95;
	// Group ironman shared storage is INV_GROUP_TEMP (659) — the container the
	// shared-storage interface works against.
	//
	// 660 is INV_PLAYER_TEMP and 661 INV_PLAYER_SNAPSHOT: those are *your own*
	// inventory as shown inside that interface, not the group's items. Reading
	// 660 as group storage recorded your inventory as the group's contents.
	private static final int CONTAINER_GROUP_STORAGE = 659;
	private static final int CONTAINER_PLAYER_TEMP = 660;
	private static final int CONTAINER_PLAYER_SNAPSHOT = 661;

	private static final String KEY_BANK = "bankItemsJson";
	private static final String KEY_GROUP = "groupItemsJson";
	private static final Type ITEM_MAP_TYPE = new TypeToken<Map<Integer, Integer>>()
	{
	}.getType();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CoxGearPlannerConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	private final Map<ItemSource, Map<Integer, Integer>> items = new EnumMap<>(ItemSource.class);
	/** Charged ids that were only seen in uncharged form, per source. */
	private final Map<ItemSource, Set<Integer>> needsCharging = new EnumMap<>(ItemSource.class);

	private CoxGearPlannerPanel panel;
	private NavigationButton navButton;

	@Provides
	CoxGearPlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CoxGearPlannerConfig.class);
	}

	@Override
	protected void startUp()
	{
		if (config.rememberBank())
		{
			items.put(ItemSource.BANK, loadPersisted(KEY_BANK));
			items.put(ItemSource.GROUP_STORAGE, loadPersisted(KEY_GROUP));
		}

		panel = new CoxGearPlannerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("CoX Gear Planner")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		SwingUtilities.invokeLater(panel::refreshStatus);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		ItemSource source;
		switch (event.getContainerId())
		{
			case CONTAINER_INVENTORY:
				source = ItemSource.INVENTORY;
				break;
			case CONTAINER_EQUIPMENT:
				source = ItemSource.EQUIPMENT;
				break;
			case CONTAINER_BANK:
				source = ItemSource.BANK;
				break;
			case CONTAINER_GROUP_STORAGE:
				source = ItemSource.GROUP_STORAGE;
				break;
			case CONTAINER_PLAYER_TEMP:
			case CONTAINER_PLAYER_SNAPSHOT:
				// Your own inventory inside the shared-storage UI — container
				// 93 already tracks that, so ignore these entirely.
				return;
			default:
				return;
		}

		Set<Integer> uncharged = new HashSet<>();
		Map<Integer, Integer> snapshot = snapshot(event.getItemContainer(), uncharged);

		// The bank and shared-storage containers briefly report empty while
		// their interface is opening or closing. Those transients would wipe a
		// good snapshot, so an empty reading never replaces known contents —
		// use "Forget stored bank/group data" to clear them deliberately.
		if (snapshot.isEmpty() && isRemembered(source))
		{
			Map<Integer, Integer> known = items.get(source);
			if (known != null && !known.isEmpty())
			{
				return;
			}
		}

		items.put(source, snapshot);
		needsCharging.put(source, uncharged);

		if (config.rememberBank())
		{
			if (source == ItemSource.BANK)
			{
				persist(KEY_BANK, snapshot);
			}
			else if (source == ItemSource.GROUP_STORAGE)
			{
				persist(KEY_GROUP, snapshot);
			}
		}

		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::refreshStatus);
		}
	}

	private Map<Integer, Integer> snapshot(ItemContainer container, Set<Integer> needsCharging)
	{
		Map<Integer, Integer> result = new HashMap<>();
		if (container == null)
		{
			return result;
		}

		for (Item item : container.getItems())
		{
			if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			// Collapse noted items and placeholders onto the base item id
			int canonical = itemManager.canonicalize(item.getId());
			// ...then collapse uncharged/inactive forms onto the charged item,
			// so an uncharged scythe counts as owning a scythe.
			int charged = ChargedVariants.canonical(canonical);
			if (charged != canonical)
			{
				needsCharging.add(charged);
			}
			result.merge(charged, item.getQuantity(), Integer::sum);
		}
		return result;
	}

	/**
	 * Thread-safe-enough copy for the Swing side: the maps are replaced
	 * wholesale on the client thread, never mutated in place.
	 */
	public Map<ItemSource, Map<Integer, Integer>> getItemsSnapshot()
	{
		Map<ItemSource, Map<Integer, Integer>> copy = new EnumMap<>(ItemSource.class);
		for (Map.Entry<ItemSource, Map<Integer, Integer>> entry : items.entrySet())
		{
			copy.put(entry.getKey(), entry.getValue());
		}
		return copy;
	}

	public CoxGearPlannerConfig getConfig()
	{
		return config;
	}

	/**
	 * Items you own only in uncharged form — they count as owned for planning,
	 * but have to be charged before the raid.
	 */
	public Set<Integer> getNeedsCharging()
	{
		Set<Integer> union = new HashSet<>();
		for (Map.Entry<ItemSource, Set<Integer>> entry : needsCharging.entrySet())
		{
			union.addAll(entry.getValue());
		}
		// A charged copy anywhere means there is nothing to charge
		for (Map.Entry<ItemSource, Set<Integer>> entry : needsCharging.entrySet())
		{
			Map<Integer, Integer> pool = items.get(entry.getKey());
			if (pool == null)
			{
				continue;
			}
			for (Integer id : pool.keySet())
			{
				if (!entry.getValue().contains(id))
				{
					union.remove(id);
				}
			}
		}
		return union;
	}

	/** Sources whose contents are remembered between sessions. */
	private static boolean isRemembered(ItemSource source)
	{
		return source == ItemSource.BANK || source == ItemSource.GROUP_STORAGE;
	}

	/**
	 * Drops the remembered bank and shared-storage contents, in memory and in
	 * the saved config. Reopen each once to resync.
	 */
	public void forgetStoredItems()
	{
		items.remove(ItemSource.BANK);
		items.remove(ItemSource.GROUP_STORAGE);
		configManager.unsetConfiguration(CoxGearPlannerConfig.GROUP, KEY_BANK);
		configManager.unsetConfiguration(CoxGearPlannerConfig.GROUP, KEY_GROUP);
	}

	/**
	 * Builds the gear suggestion and room time estimates. Player levels must
	 * be read on the client thread; the callback is invoked on the Swing EDT.
	 */
	public void computePlan(Set<CoxRoom> rooms, Consumer<PlanResult> callback)
	{
		clientThread.invokeLater(() ->
		{
			PlayerSnapshot player = snapshotPlayer();
			Map<ItemSource, Map<Integer, Integer>> snapshot = getItemsSnapshot();
			boolean includeGroup = config.includeGroupStorage();

			PlanExplanation explanation = config.showDebug() ? new PlanExplanation() : null;

			RoomTimeEstimator estimator = new RoomTimeEstimator(itemManager);
			estimator.setOlmFourTick(config.olmFourTick());
			List<SetupBuilder.Section> sections = SetupBuilder.build(
				rooms, snapshot, includeGroup, estimator.getResolver());
			List<RoomTimeEstimator.RoomTime> times = estimator.estimate(
				rooms, snapshot, includeGroup, player,
				config.partySize(), config.assumeElitePrayers(), explanation);
			// The advisor now picks the base outfit itself, by trying each style
			// and keeping whichever gives the lowest total raid time.
			SwitchAdvisor.Result switches = new SwitchAdvisor(estimator).advise(
				times, snapshot, includeGroup, player,
				config.partySize(), config.assumeElitePrayers(), config.minSwitchSeconds(),
				config.maxSwitchItems(), config.totalSwapItems(), explanation);

			List<SwitchAdvisor.Advice> advice = switches.getAdvice();
			GearNeed primary = switches.getPrimary();
			RaidLoadoutBuilder.RaidLoadout loadout = RaidLoadoutBuilder.build(
				rooms, times, advice, primary, snapshot, includeGroup,
				estimator.getResolver(), getNeedsCharging());

			if (explanation != null)
			{
				for (GearNeed style : GearNeed.values())
				{
					if (style.isCombatStyle())
					{
						estimator.describeWeaponPool(style, snapshot, includeGroup, explanation);
						estimator.getResolver().describeChoices(style, snapshot, includeGroup, explanation);
					}
				}
			}

			PlanResult result = new PlanResult(sections, times, advice, primary, loadout, explanation);
			result.setExportText(PlanExport.render(rooms, player, config, result));
			SwingUtilities.invokeLater(() -> callback.accept(result));
		});
	}

	private PlayerSnapshot snapshotPlayer()
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			return new PlayerSnapshot(
				assumedLevel(Skill.ATTACK),
				assumedLevel(Skill.STRENGTH),
				assumedLevel(Skill.RANGED),
				assumedLevel(Skill.MAGIC),
				client.getRealSkillLevel(Skill.MINING));
		}
		// Logged out: assume maxed stats, plus overload if configured
		int level = config.assumeOverload() ? overloaded(99) : 99;
		return new PlayerSnapshot(level, level, level, level, 99);
	}

	/**
	 * The live boosted level — or, if the player isn't currently boosted and
	 * overloads are assumed (you drink one at the start of every raid), the
	 * overloaded base level.
	 */
	private int assumedLevel(Skill skill)
	{
		int real = client.getRealSkillLevel(skill);
		int boosted = client.getBoostedSkillLevel(skill);
		int assumed = config.assumeOverload() ? overloaded(real) : real;
		return Math.max(boosted, assumed);
	}

	/** Overload (+): +6 plus 16% of the base level. */
	private static int overloaded(int level)
	{
		return level + 6 + (int) (level * 0.16);
	}

	private void persist(String key, Map<Integer, Integer> map)
	{
		configManager.setConfiguration(CoxGearPlannerConfig.GROUP, key, gson.toJson(map));
	}

	private Map<Integer, Integer> loadPersisted(String key)
	{
		String json = configManager.getConfiguration(CoxGearPlannerConfig.GROUP, key);
		if (json == null || json.isEmpty())
		{
			return Collections.emptyMap();
		}

		try
		{
			Map<Integer, Integer> map = gson.fromJson(json, ITEM_MAP_TYPE);
			return map != null ? map : Collections.emptyMap();
		}
		catch (RuntimeException e)
		{
			return Collections.emptyMap();
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(93, 57, 24));
		g.fillRoundRect(1, 1, 22, 22, 6, 6);
		g.setColor(new Color(219, 175, 82));
		g.setStroke(new BasicStroke(1.5f));
		g.drawRoundRect(1, 1, 22, 22, 6, 6);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
		g.drawString("CX", 4, 16);
		g.dispose();
		return image;
	}
}
