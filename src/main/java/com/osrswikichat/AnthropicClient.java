package com.osrswikichat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Singleton
class AnthropicClient implements LLMClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String URL = "https://api.anthropic.com/v1/messages";
	private static final String MODEL = "claude-haiku-4-5-20251001";

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	AnthropicClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	@Override
	public Call chat(String systemPrompt, String userPrompt, String apiKey, Listener listener)
	{
		JsonObject body = new JsonObject();
		body.addProperty("model", MODEL);
		body.addProperty("stream", true);
		body.addProperty("max_tokens", 2048);
		body.addProperty("temperature", 0.3);
		body.addProperty("system", systemPrompt);

		JsonArray messages = new JsonArray();
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", userPrompt);
		messages.add(userMsg);
		body.add("messages", messages);

		Request request = new Request.Builder()
			.url(URL)
			.header("x-api-key", apiKey)
			.header("anthropic-version", "2023-06-01")
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call call = http.newCall(request);
		call.enqueue(new SseStreamCallback(listener, gson, "Anthropic")
		{
			@Override
			String extractDelta(JsonObject event)
			{
				if (!event.has("type"))
				{
					return null;
				}
				if (!"content_block_delta".equals(event.get("type").getAsString()))
				{
					return null;
				}
				JsonObject delta = event.getAsJsonObject("delta");
				if (delta == null || !delta.has("text"))
				{
					return null;
				}
				return delta.get("text").getAsString();
			}
		});
		return call;
	}
}
