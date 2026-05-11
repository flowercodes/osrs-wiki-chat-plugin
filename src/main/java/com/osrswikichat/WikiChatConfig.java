package com.osrswikichat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(WikiChatConfig.GROUP)
public interface WikiChatConfig extends Config
{
	String GROUP = "osrs-wiki-chat";

	@ConfigSection(
		name = "Backend",
		description = "Where the plugin sends your questions",
		position = 0
	)
	String backendSection = "backend";

	@ConfigSection(
		name = "Provider (advanced)",
		description = "Use your own API key instead of the shared Gemini quota",
		position = 1,
		closedByDefault = true
	)
	String providerSection = "provider";

	@ConfigItem(
		keyName = "backendUrl",
		name = "Backend URL",
		description = "URL of the Wiki Chat worker. Only change this if you're self-hosting.",
		section = backendSection,
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default String backendUrl()
	{
		return "https://osrs-wiki-chat.brian-f29.workers.dev";
	}

	@ConfigItem(
		keyName = "provider",
		name = "Provider",
		description = "Which LLM to use. You must provide your own API key — see the Wiki Chat panel for setup instructions.",
		section = providerSection
	)
	default Provider provider()
	{
		return Provider.GEMINI;
	}

	@ConfigItem(
		keyName = "userApiKey",
		name = "Your API key",
		description = "Your personal API key from Gemini/OpenAI/Anthropic. Sent to the backend with each request, never stored remotely.",
		section = providerSection,
		secret = true
	)
	default String userApiKey()
	{
		return "";
	}
}
