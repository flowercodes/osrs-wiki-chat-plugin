package com.osrswikichat;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Wiki Chat",
	description = "Ask questions about OSRS and get cited answers from the wiki",
	tags = {"wiki", "chat", "ai", "assistant", "help"}
)
public class WikiChatPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private WikiChatPanel panel;

	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		navButton = NavigationButton.builder()
			.tooltip("Wiki Chat")
			.icon(buildIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		log.debug("Wiki Chat started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (panel != null)
		{
			panel.cancelInflight();
		}
		log.debug("Wiki Chat stopped");
	}

	@Provides
	WikiChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WikiChatConfig.class);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] entries = event.getMenuEntries();
		String target = null;
		boolean hasExamine = false;

		for (MenuEntry entry : entries)
		{
			String option = entry.getOption();
			if (option != null && option.equalsIgnoreCase("Examine"))
			{
				hasExamine = true;
			}
			if (target == null)
			{
				String t = entry.getTarget();
				if (t != null && !t.isEmpty())
				{
					target = t;
				}
			}
		}

		if (!hasExamine || target == null)
		{
			return;
		}

		final String menuTarget = target;
		client.createMenuEntry(-1)
			.setOption("Ask AI")
			.setTarget(menuTarget)
			.setType(MenuAction.RUNELITE)
			.onClick(e -> openAskAi(menuTarget));
	}

	private void openAskAi(String rawTarget)
	{
		String clean = Text.removeTags(rawTarget).trim();
		clean = clean.replaceAll("\\s*\\(level[-:\\s]*\\d+\\)", "").trim();
		if (clean.isEmpty())
		{
			return;
		}
		String prefilled = "Tell me about " + clean + " in OSRS";
		SwingUtilities.invokeLater(() ->
		{
			if (navButton != null)
			{
				clientToolbar.openPanel(navButton);
			}
			panel.prefillAndFocus(prefilled);
		});
	}

	private static BufferedImage buildIcon()
	{
		int size = 24;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		g.setColor(new Color(245, 158, 11));
		g.fillRoundRect(1, 2, 22, 16, 8, 8);

		int[] tx = {6, 12, 6};
		int[] ty = {18, 18, 22};
		g.fillPolygon(tx, ty, 3);

		g.setColor(Color.BLACK);
		g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 11f));
		java.awt.FontMetrics fm = g.getFontMetrics();
		String label = "W";
		int w = fm.stringWidth(label);
		g.drawString(label, (size - w) / 2, 13);

		g.dispose();
		return img;
	}
}
