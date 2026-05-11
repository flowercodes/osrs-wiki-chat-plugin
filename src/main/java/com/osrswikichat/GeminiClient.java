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
class GeminiClient implements LLMClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String MODEL = "gemini-2.5-flash";
	private static final String URL_TEMPLATE =
		"https://generativelanguage.googleapis.com/v1beta/models/" + MODEL
			+ ":streamGenerateContent?alt=sse&key=";

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	GeminiClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	@Override
	public Call chat(String systemPrompt, String userPrompt, String apiKey, Listener listener)
	{
		JsonObject body = new JsonObject();

		JsonObject systemInstruction = new JsonObject();
		JsonArray sysParts = new JsonArray();
		JsonObject sysPart = new JsonObject();
		sysPart.addProperty("text", systemPrompt);
		sysParts.add(sysPart);
		systemInstruction.add("parts", sysParts);
		body.add("systemInstruction", systemInstruction);

		JsonArray contents = new JsonArray();
		JsonObject content = new JsonObject();
		content.addProperty("role", "user");
		JsonArray parts = new JsonArray();
		JsonObject part = new JsonObject();
		part.addProperty("text", userPrompt);
		parts.add(part);
		content.add("parts", parts);
		contents.add(content);
		body.add("contents", contents);

		JsonObject genConfig = new JsonObject();
		genConfig.addProperty("temperature", 0.3);
		genConfig.addProperty("maxOutputTokens", 2048);
		body.add("generationConfig", genConfig);

		Request request = new Request.Builder()
			.url(URL_TEMPLATE + apiKey)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call call = http.newCall(request);
		call.enqueue(new SseStreamCallback(listener, gson, "Gemini")
		{
			@Override
			String extractDelta(JsonObject event)
			{
				JsonArray candidates = event.getAsJsonArray("candidates");
				if (candidates == null || candidates.size() == 0)
				{
					return null;
				}
				JsonObject cand = candidates.get(0).getAsJsonObject();
				JsonObject contentObj = cand.getAsJsonObject("content");
				if (contentObj == null)
				{
					return null;
				}
				JsonArray pieces = contentObj.getAsJsonArray("parts");
				if (pieces == null || pieces.size() == 0)
				{
					return null;
				}
				JsonObject piece = pieces.get(0).getAsJsonObject();
				return piece.has("text") ? piece.get("text").getAsString() : null;
			}
		});
		return call;
	}
}
