package com.osrswikichat;

public enum Provider
{
	GEMINI(
		"Gemini",
		"gemini",
		"https://aistudio.google.com/app/apikey",
		"Free tier: 1,500 questions/day, no credit card."),
	OPENAI(
		"OpenAI",
		"openai",
		"https://platform.openai.com/api-keys",
		"Pay-per-use, ~$0.001/question. Credit card required."),
	ANTHROPIC(
		"Anthropic (Claude)",
		"anthropic",
		"https://console.anthropic.com/settings/keys",
		"Pay-per-use, ~$0.006/question. Credit card required.");

	private final String label;
	private final String wireName;
	private final String keyUrl;
	private final String info;

	Provider(String label, String wireName, String keyUrl, String info)
	{
		this.label = label;
		this.wireName = wireName;
		this.keyUrl = keyUrl;
		this.info = info;
	}

	public String wireName()
	{
		return wireName;
	}

	public String keyUrl()
	{
		return keyUrl;
	}

	public String info()
	{
		return info;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
