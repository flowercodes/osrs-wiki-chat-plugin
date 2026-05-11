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
class OpenAIClient implements LLMClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String URL = "https://api.openai.com/v1/chat/completions";
	private static final String MODEL = "gpt-4o-mini";

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	OpenAIClient(OkHttpClient http, Gson gson)
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
		body.addProperty("temperature", 0.3);
		body.addProperty("max_tokens", 2048);

		JsonArray messages = new JsonArray();

		JsonObject sysMsg = new JsonObject();
		sysMsg.addProperty("role", "system");
		sysMsg.addProperty("content", systemPrompt);
		messages.add(sysMsg);

		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", userPrompt);
		messages.add(userMsg);

		body.add("messages", messages);

		Request request = new Request.Builder()
			.url(URL)
			.header("authorization", "Bearer " + apiKey)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call call = http.newCall(request);
		call.enqueue(new SseStreamCallback(listener, gson, "OpenAI")
		{
			@Override
			String extractDelta(JsonObject event)
			{
				JsonArray choices = event.getAsJsonArray("choices");
				if (choices == null || choices.size() == 0)
				{
					return null;
				}
				JsonObject choice = choices.get(0).getAsJsonObject();
				JsonObject delta = choice.getAsJsonObject("delta");
				if (delta == null || !delta.has("content"))
				{
					return null;
				}
				return delta.get("content").getAsString();
			}
		});
		return call;
	}
}
