package com.osrswikichat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class WikiChatClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;
	private final WikiChatConfig config;
	private final LLMRegistry llmRegistry;

	@Inject
	WikiChatClient(OkHttpClient http, Gson gson, WikiChatConfig config, LLMRegistry llmRegistry)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.llmRegistry = llmRegistry;
	}

	public interface Listener
	{
		void onChunk(String text);

		void onDone(List<Source> sources);

		void onError(String message);
	}

	public static final class Handle
	{
		private volatile Call activeCall;

		void setActive(Call call)
		{
			this.activeCall = call;
		}

		public void cancel()
		{
			Call c = activeCall;
			if (c != null && !c.isCanceled())
			{
				c.cancel();
			}
		}

		public boolean isCanceled()
		{
			Call c = activeCall;
			return c != null && c.isCanceled();
		}
	}

	public Handle ask(String question, Listener listener)
	{
		Handle handle = new Handle();

		Provider provider = config.provider();
		String apiKey = config.userApiKey();
		if (apiKey == null || apiKey.trim().isEmpty())
		{
			listener.onError("No API key set. Click the ⚙ button to set one up.");
			return handle;
		}

		HttpUrl base = HttpUrl.parse(config.backendUrl());
		if (base == null)
		{
			listener.onError("Invalid backend URL: " + config.backendUrl());
			return handle;
		}

		JsonObject body = new JsonObject();
		body.addProperty("question", question);

		Request contextRequest = new Request.Builder()
			.url(base.newBuilder().addPathSegment("context").build())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call contextCall = http.newCall(contextRequest);
		handle.setActive(contextCall);

		contextCall.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call c, IOException e)
			{
				if (c.isCanceled())
				{
					return;
				}
				listener.onError("Couldn't reach the Wiki Chat backend: " + e.getMessage());
			}

			@Override
			public void onResponse(Call c, Response response) throws IOException
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						String msg = "Backend HTTP " + r.code();
						ResponseBody errBody = r.body();
						if (errBody != null)
						{
							String preview = errBody.string();
							if (preview.length() > 200)
							{
								preview = preview.substring(0, 200);
							}
							msg = msg + ": " + preview;
						}
						listener.onError(msg);
						return;
					}

					ResponseBody respBody = r.body();
					if (respBody == null)
					{
						listener.onError("Empty backend response");
						return;
					}

					JsonObject parsed = gson.fromJson(respBody.string(), JsonObject.class);
					if (parsed == null)
					{
						listener.onError("Backend returned no JSON");
						return;
					}

					List<Source> sources = new ArrayList<>();
					List<String> chunkTexts = new ArrayList<>();
					JsonArray chunks = parsed.getAsJsonArray("chunks");
					if (chunks != null)
					{
						for (int i = 0; i < chunks.size(); i++)
						{
							JsonObject ch = chunks.get(i).getAsJsonObject();
							String title = ch.has("title") ? ch.get("title").getAsString() : "(unknown)";
							String url = ch.has("url") ? ch.get("url").getAsString() : "";
							String historyUrl = ch.has("historyUrl")
								? ch.get("historyUrl").getAsString() : "";
							String text = ch.has("text") ? ch.get("text").getAsString() : "";
							if (text.isEmpty())
							{
								continue;
							}
							sources.add(new Source(title, url, historyUrl));
							chunkTexts.add(text);
						}
					}

					if (sources.isEmpty())
					{
						listener.onError("No wiki context found for that question.");
						return;
					}

					if (handle.isCanceled())
					{
						return;
					}

					List<Source> deduped = uniqueByTitle(sources);
					String userPrompt = PromptBuilder.userPrompt(question, sources, chunkTexts);
					String systemPrompt = PromptBuilder.systemPrompt();

					LLMClient llm = llmRegistry.get(provider);
					Call llmCall = llm.chat(systemPrompt, userPrompt, apiKey.trim(),
						new LLMClient.Listener()
						{
							@Override
							public void onChunk(String text)
							{
								listener.onChunk(text);
							}

							@Override
							public void onDone()
							{
								listener.onDone(deduped);
							}

							@Override
							public void onError(String message)
							{
								listener.onError(message);
							}
						});
					handle.setActive(llmCall);
				}
				catch (Exception e)
				{
					if (!c.isCanceled())
					{
						listener.onError("Backend parse error: " + e.getMessage());
					}
				}
			}
		});

		return handle;
	}

	private static List<Source> uniqueByTitle(List<Source> sources)
	{
		List<Source> out = new ArrayList<>();
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Source s : sources)
		{
			if (s == null || s.getTitle() == null)
			{
				continue;
			}
			if (seen.add(s.getTitle()))
			{
				out.add(s);
			}
		}
		return Collections.unmodifiableList(out);
	}
}
