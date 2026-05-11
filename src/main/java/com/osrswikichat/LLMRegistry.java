package com.osrswikichat;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class LLMRegistry
{
	private final GeminiClient gemini;
	private final OpenAIClient openai;
	private final AnthropicClient anthropic;

	@Inject
	LLMRegistry(GeminiClient gemini, OpenAIClient openai, AnthropicClient anthropic)
	{
		this.gemini = gemini;
		this.openai = openai;
		this.anthropic = anthropic;
	}

	LLMClient get(Provider provider)
	{
		switch (provider)
		{
			case GEMINI:
				return gemini;
			case OPENAI:
				return openai;
			case ANTHROPIC:
				return anthropic;
			default:
				throw new IllegalArgumentException("unknown provider: " + provider);
		}
	}
}
