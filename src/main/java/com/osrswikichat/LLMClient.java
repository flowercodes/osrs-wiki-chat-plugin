package com.osrswikichat;

import okhttp3.Call;

interface LLMClient
{
	Call chat(String systemPrompt, String userPrompt, String apiKey, Listener listener);

	interface Listener
	{
		void onChunk(String text);

		void onDone();

		void onError(String message);
	}
}
