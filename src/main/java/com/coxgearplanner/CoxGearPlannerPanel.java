package com.coxgearplanner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class CoxGearPlannerPanel extends PluginPanel
{
	private static final Color COLOR_ON_HAND = new Color(105, 200, 105);
	private static final Color COLOR_BANK = new Color(220, 220, 220);
	private static final Color COLOR_GROUP = new Color(130, 190, 255);
	private static final Color COLOR_MISSING = new Color(235, 100, 100);

	private final CoxGearPlannerPlugin plugin;
	private final Map<CoxRoom, JCheckBox> roomBoxes = new EnumMap<>(CoxRoom.class);
	private final JLabel statusLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();

	CoxGearPlannerPanel(CoxGearPlannerPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(buildTopPanel(), BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(resultsPanel, BorderLayout.CENTER);
	}

	private JPanel buildTopPanel()
	{
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("CoX Gear Planner v" + CoxGearPlannerPlugin.VERSION);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(title);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(statusLabel);
		top.add(Box.createVerticalStrut(8));

		top.add(sectionLabel("Rooms in your layout"));
		top.add(roomGrid(true));
		top.add(Box.createVerticalStrut(4));
		top.add(sectionLabel("Puzzle rooms"));
		top.add(roomGrid(false));
		top.add(Box.createVerticalStrut(8));

		JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 4));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton importButton = new JButton("Import layout from clipboard");
		importButton.setToolTipText("Reads copied scout text (e.g. from a scouting Discord or the Raids plugin) and ticks any room names it finds");
		importButton.addActionListener(e -> importFromClipboard());
		buttons.add(importButton);

		JButton clearButton = new JButton("Clear rooms");
		clearButton.addActionListener(e ->
		{
			for (Map.Entry<CoxRoom, JCheckBox> entry : roomBoxes.entrySet())
			{
				entry.getValue().setSelected(entry.getKey() == CoxRoom.OLM);
			}
		});
		buttons.add(clearButton);

		JButton forgetButton = new JButton("Forget stored bank/group data");
		forgetButton.setToolTipText("Clears the remembered bank and group storage contents; reopen each once to resync");
		forgetButton.addActionListener(e ->
		{
			plugin.forgetStoredItems();
			refreshStatus();
		});
		buttons.add(forgetButton);

		JButton suggestButton = new JButton("Suggest gear setup");
		suggestButton.addActionListener(e -> suggest());
		buttons.add(suggestButton);

		top.add(buttons);
		return top;
	}

	private JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private JPanel roomGrid(boolean bosses)
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 2, 0));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);

		for (CoxRoom room : CoxRoom.values())
		{
			if (room.isBoss() != bosses)
			{
				continue;
			}
			JCheckBox box = new JCheckBox(room.getDisplayName());
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setForeground(Color.WHITE);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			if (room == CoxRoom.OLM)
			{
				// Olm ends every raid
				box.setSelected(true);
			}
			roomBoxes.put(room, box);
			grid.add(box);
		}
		return grid;
	}

	void refreshStatus()
	{
		Map<ItemSource, Map<Integer, Integer>> items = plugin.getItemsSnapshot();
		StringBuilder sb = new StringBuilder("<html>Known items — ");
		boolean any = false;
		for (ItemSource source : ItemSource.values())
		{
			Map<Integer, Integer> pool = items.get(source);
			int size = pool == null ? 0 : pool.size();
			if (any)
			{
				sb.append(" · ");
			}
			sb.append(source.getDisplayName()).append(": ").append(size);
			any = true;
		}
		Map<Integer, Integer> group = items.get(ItemSource.GROUP_STORAGE);
		if (group == null || group.isEmpty())
		{
			sb.append("<br><font color='#eb6464'>Group storage not synced</font>"
				+ " — open the shared storage chest once.");
		}
		if (!plugin.getConfig().includeGroupStorage())
		{
			sb.append("<br><font color='#eb6464'>Group storage is disabled in config.</font>");
		}
		sb.append("<br>Bank and group contents are remembered from the last time"
			+ " you opened them.</html>");
		statusLabel.setText(sb.toString());
	}

	private void importFromClipboard()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit()
				.getSystemClipboard()
				.getData(DataFlavor.stringFlavor);
		}
		catch (Exception e)
		{
			statusLabel.setText("<html><font color='#eb6464'>Clipboard has no text to import.</font></html>");
			return;
		}

		if (text == null)
		{
			return;
		}

		String lower = text.toLowerCase();
		int matched = 0;
		for (CoxRoom room : CoxRoom.values())
		{
			for (String alias : room.getAliases())
			{
				if (lower.contains(alias))
				{
					roomBoxes.get(room).setSelected(true);
					matched++;
					break;
				}
			}
		}
		statusLabel.setText("<html>Matched " + matched + " room(s) from clipboard text.</html>");
	}

	private void suggest()
	{
		Set<CoxRoom> rooms = EnumSet.noneOf(CoxRoom.class);
		for (Map.Entry<CoxRoom, JCheckBox> entry : roomBoxes.entrySet())
		{
			if (entry.getValue().isSelected())
			{
				rooms.add(entry.getKey());
			}
		}

		resultsPanel.removeAll();

		if (rooms.isEmpty())
		{
			JLabel none = new JLabel("Select the rooms in your raid first.");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			none.setFont(FontManager.getRunescapeSmallFont());
			resultsPanel.add(none);
			revalidateResults();
			return;
		}

		// Player levels are read on the client thread; results come back on the EDT
		plugin.computePlan(rooms, this::renderResults);
	}

	private void renderResults(PlanResult result)
	{
		resultsPanel.removeAll();
		List<SetupBuilder.Section> sections = result.getSections();
		List<RoomTimeEstimator.RoomTime> times = result.getTimes();

		renderLoadout(result.getLoadout());

		// Per-style sections are derived from the loadout above, so they only
		// ever name gear that is actually equipped or in the inventory list.
		// The standalone SetupBuilder sections are the fallback for when no
		// room has a feasible kill and there is no loadout to derive from.
		if (result.getLoadout() != null)
		{
			sections = result.getLoadout().getStyleSections();

			JLabel caption = new JLabel("<html><br>Below: what you're actually wearing"
				+ " for each style after swapping. Every item named here is either"
				+ " equipped or in the inventory list above.</html>");
			caption.setFont(FontManager.getRunescapeSmallFont());
			caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			caption.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(caption);
		}

		for (SetupBuilder.Section section : sections)
		{
			JLabel header = new JLabel("<html>" + section.getTitle() + "</html>");
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setForeground(ColorScheme.BRAND_ORANGE);
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
			resultsPanel.add(header);

			for (SetupBuilder.Line line : section.getLines())
			{
				if (line.isMissing() && plugin.getConfig().hideMissing())
				{
					continue;
				}
				resultsPanel.add(lineLabel(line));
			}
		}

		if (!times.isEmpty())
		{
			JLabel header = new JLabel("Estimated room times (party of "
				+ plugin.getConfig().partySize() + ")");
			header.setFont(FontManager.getRunescapeBoldFont());
			header.setForeground(ColorScheme.BRAND_ORANGE);
			header.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
			resultsPanel.add(header);

			double total = 0;
			boolean anyInfeasible = false;
			for (RoomTimeEstimator.RoomTime time : times)
			{
				JLabel label;
				if (time.isFeasible())
				{
					total += time.getSeconds();
					label = new JLabel("<html><b>" + time.getDisplayName() + ":</b> ~"
						+ formatSeconds(time.getSeconds()) + " — " + time.getDetail() + "</html>");
					label.setForeground(COLOR_BANK);
				}
				else
				{
					anyInfeasible = true;
					label = new JLabel("<html><b>" + time.getDisplayName() + ":</b> "
						+ time.getDetail() + "</html>");
					label.setForeground(COLOR_MISSING);
				}
				label.setFont(FontManager.getRunescapeSmallFont());
				label.setAlignmentX(Component.LEFT_ALIGNMENT);
				resultsPanel.add(label);
			}

			JLabel totalLabel = new JLabel("<html><b>Total combat time:</b> ~"
				+ formatSeconds(total) + (anyInfeasible ? " (excl. red rooms)" : "") + "</html>");
			totalLabel.setFont(FontManager.getRunescapeSmallFont());
			totalLabel.setForeground(COLOR_ON_HAND);
			totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(totalLabel);
		}

		renderSwitchAdvice(result);
		renderDebug(result);

		JLabel legend = new JLabel("<html><br>"
			+ colored("■", COLOR_ON_HAND) + " on you &nbsp;"
			+ colored("■", COLOR_BANK) + " bank &nbsp;"
			+ colored("■", COLOR_GROUP) + " group &nbsp;"
			+ colored("■", COLOR_MISSING) + " missing</html>");
		legend.setFont(FontManager.getRunescapeSmallFont());
		legend.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		legend.setAlignmentX(Component.LEFT_ALIGNMENT);
		resultsPanel.add(legend);

		revalidateResults();
	}

	private JLabel lineLabel(SetupBuilder.Line line)
	{
		Color color;
		String suffix;
		if (line.isMissing())
		{
			color = COLOR_MISSING;
			suffix = "";
		}
		else if (line.getSource() == ItemSource.BANK)
		{
			color = COLOR_BANK;
			suffix = " [bank]";
		}
		else if (line.getSource() == ItemSource.GROUP_STORAGE)
		{
			color = COLOR_GROUP;
			suffix = " [group]";
		}
		else if (line.getSource() == null)
		{
			// informational line, e.g. shield suppressed by a two-hander
			color = ColorScheme.LIGHT_GRAY_COLOR;
			suffix = "";
		}
		else
		{
			color = COLOR_ON_HAND;
			suffix = " [on you]";
		}

		String qty = line.getQuantity() > 1 && "Ammo".equals(line.getLabel())
			? " x" + line.getQuantity()
			: "";

		JLabel label = new JLabel("<html><b>" + line.getLabel() + ":</b> "
			+ line.getItemName() + qty + suffix + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private void renderLoadout(RaidLoadoutBuilder.RaidLoadout loadout)
	{
		if (loadout == null)
		{
			return;
		}

		int wornCount = 0;
		for (SetupBuilder.Line line : loadout.getEquipped())
		{
			if (!line.isMissing() && line.getSource() != null)
			{
				wornCount++;
			}
		}

		JLabel header = new JLabel("EQUIPPED (" + wornCount + "/11 slots) — wear "
			+ loadout.getPrimaryStyle().getDisplayName().toLowerCase());
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		resultsPanel.add(header);

		for (SetupBuilder.Line line : loadout.getEquipped())
		{
			if (line.isMissing() && plugin.getConfig().hideMissing())
			{
				continue;
			}
			resultsPanel.add(lineLabel(line));
		}

		JLabel invHeader = new JLabel("INVENTORY (" + loadout.getUsedSlots()
			+ "/" + RaidLoadoutBuilder.INVENTORY_SIZE + " used by gear)");
		invHeader.setFont(FontManager.getRunescapeBoldFont());
		invHeader.setForeground(ColorScheme.BRAND_ORANGE);
		invHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		invHeader.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		resultsPanel.add(invHeader);

		int slot = 0;
		for (RaidLoadoutBuilder.Entry entry : loadout.getInventory())
		{
			if (entry.isMissing() && plugin.getConfig().hideMissing())
			{
				continue;
			}
			Color color = entry.isMissing() ? COLOR_MISSING
				: entry.getSource() == ItemSource.BANK ? COLOR_BANK
				: entry.getSource() == ItemSource.GROUP_STORAGE ? COLOR_GROUP
				: COLOR_ON_HAND;
			String prefix = entry.isMissing() ? "&nbsp;— " : (++slot) + ". ";
			JLabel label = new JLabel("<html>" + prefix + entry.getName()
				+ " <font color='#a0a0a0'>— " + entry.getNote() + "</font></html>");
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(color);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(label);
		}

		JLabel free = new JLabel(loadout.isOverCapacity()
			? "Gear alone exceeds 28 slots — drop some switches"
			: loadout.getFreeSlots() + " slots free for brews, restores and food");
		free.setFont(FontManager.getRunescapeSmallFont());
		free.setForeground(loadout.isOverCapacity() ? COLOR_MISSING : ColorScheme.LIGHT_GRAY_COLOR);
		free.setAlignmentX(Component.LEFT_ALIGNMENT);
		resultsPanel.add(free);
	}

	private void renderDebug(PlanResult result)
	{
		PlanExplanation explanation = result.getExplanation();
		if (explanation == null || explanation.isEmpty())
		{
			return;
		}

		addDebugBlock("Debug — weapons found in your storage", explanation.getWeaponPool());
		addDebugBlock("Debug — weapon choice per room", explanation.getWeaponChoices());
		addDebugBlock("Debug — switch decisions", explanation.getSwitchChoices());
		addDebugBlock("Debug — best owned item per slot", explanation.getGearChoices());
	}

	private void addDebugBlock(String title, List<String> lines)
	{
		if (lines.isEmpty())
		{
			return;
		}

		JLabel header = new JLabel(title);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		resultsPanel.add(header);

		for (String line : lines)
		{
			JLabel label = new JLabel("<html>" + escape(line) + "</html>");
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(label);
		}
	}

	private static String escape(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void renderSwitchAdvice(PlanResult result)
	{
		List<SwitchAdvisor.Advice> advices = result.getSwitchAdvice();
		if (advices.isEmpty() || result.getPrimaryStyle() == null)
		{
			return;
		}

		int cap = plugin.getConfig().maxSwitchItems();
		JLabel header = new JLabel("Switch advice (base outfit: "
			+ result.getPrimaryStyle().getDisplayName()
			+ (cap > 0 ? ", max " + cap + " items per switch" : "") + ")");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
		resultsPanel.add(header);

		int skippable = 0;
		double skippedCost = 0;
		for (SwitchAdvisor.Advice advice : advices)
		{
			String styleName = advice.getStyle().getDisplayName().toLowerCase();
			JLabel label;
			if (advice.isAlreadyShared())
			{
				label = new JLabel("<html>" + advice.getSlot().getDisplayName() + ": "
					+ advice.getItemName() + " — already worn</html>");
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
			else if (advice.isWorthIt())
			{
				label = new JLabel("<html><b>Carry</b> " + advice.getItemName()
					+ " (" + styleName + " " + advice.getSlot().getDisplayName().toLowerCase()
					+ "): saves ~" + formatSaved(advice.getSecondsSaved()) + "</html>");
				label.setForeground(COLOR_ON_HAND);
			}
			else
			{
				skippable++;
				skippedCost += advice.getSecondsSaved();
				String instead = advice.getWearInstead() != null
					? " — keep " + advice.getWearInstead() + " on"
					: "";
				if (advice.isOverLimit())
				{
					// Worth carrying on value, but the switch cap was full
					label = new JLabel("<html><b>Over limit</b> " + advice.getItemName()
						+ " (" + styleName + " " + advice.getSlot().getDisplayName().toLowerCase()
						+ "): worth ~" + formatSaved(advice.getSecondsSaved())
						+ " but exceeds your " + plugin.getConfig().maxSwitchItems()
						+ "-item switch" + instead + "</html>");
					label.setForeground(COLOR_GROUP);
				}
				else
				{
					label = new JLabel("<html><b>Skip</b> " + advice.getItemName()
						+ " (" + styleName + " " + advice.getSlot().getDisplayName().toLowerCase()
						+ "): only ~" + formatSaved(advice.getSecondsSaved()) + instead + "</html>");
					label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			}
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(label);
		}

		if (skippable > 0)
		{
			JLabel summary = new JLabel("<html><b>" + skippable + " switch(es) not worth it</b> — frees "
				+ skippable + " inventory slot(s) for ~" + formatSaved(skippedCost) + " slower</html>");
			summary.setFont(FontManager.getRunescapeSmallFont());
			summary.setForeground(COLOR_GROUP);
			summary.setAlignmentX(Component.LEFT_ALIGNMENT);
			summary.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
			resultsPanel.add(summary);
		}
	}

	private static String formatSaved(double seconds)
	{
		return seconds < 10 ? String.format("%.1fs", seconds) : Math.round(seconds) + "s";
	}

	private static String formatSeconds(double seconds)
	{
		int total = (int) Math.round(seconds);
		return total / 60 + ":" + String.format("%02d", total % 60);
	}

	private static String colored(String text, Color color)
	{
		return "<font color='" + String.format("#%02x%02x%02x",
			color.getRed(), color.getGreen(), color.getBlue()) + "'>" + text + "</font>";
	}

	private void revalidateResults()
	{
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}
}
