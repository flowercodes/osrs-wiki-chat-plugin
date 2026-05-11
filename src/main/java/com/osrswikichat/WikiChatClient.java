package com.osrswikichat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
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
	private static final Type SOURCES_TYPE = new TypeToken<List<Source>>(){}.getType();

	private final OkHttpClient http;
	private final Gson gson;
	private final WikiChatConfig config;

	@Inject
	WikiChatClient(OkHttpClient http, Gson gson, WikiChatConfig config)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
	}

	public interface Listener
	{
		void onChunk(String text);

		void onDone(List<Source> sources);

		void onError(String message);
	}

	public Call ask(String question, Listener listener)
	{
		HttpUrl base = HttpUrl.parse(config.backendUrl());
		if (base == null)
		{
			listener.onError("Invalid backend URL: " + config.backendUrl());
			return null;
		}

		JsonObject body = new JsonObject();
		body.addProperty("question", question);

		Provider provider = config.provider();
		if (provider != null && provider.wireName() != null)
		{
			body.addProperty("provider", provider.wireName());
		}

		String key = config.userApiKey();
		if (key != null && !key.trim().isEmpty())
		{
			body.addProperty("userApiKey", key.trim());
		}

		Request request = new Request.Builder()
			.url(base.newBuilder().addPathSegment("ask").build())
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();

		Call call = http.newCall(request);
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call c, IOException e)
			{
				if (c.isCanceled())
				{
					return;
				}
				log.debug("ask failed", e);
				listener.onError("Request failed: " + e.getMessage());
			}

			@Override
			public void onResponse(Call c, Response response) throws IOException
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						String msg = "HTTP " + r.code();
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

					List<Source> sources = parseSources(r.header("x-sources"));
					ResponseBody respBody = r.body();
					if (respBody == null)
					{
						listener.onError("Empty response body");
						return;
					}

					try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8)))
					{
						char[] buf = new char[512];
						int n;
						while ((n = reader.read(buf)) != -1)
						{
							if (c.isCanceled())
							{
								return;
							}
							listener.onChunk(new String(buf, 0, n));
						}
					}
					listener.onDone(sources);
				}
				catch (IOException e)
				{
					if (!c.isCanceled())
					{
						listener.onError("Read failed: " + e.getMessage());
					}
				}
			}
		});

		return call;
	}

	private List<Source> parseSources(String header)
	{
		if (header == null || header.isEmpty())
		{
			return Collections.emptyList();
		}
		try
		{
			List<Source> parsed = gson.fromJson(header, SOURCES_TYPE);
			return parsed != null ? parsed : Collections.emptyList();
		}
		catch (Exception e)
		{
			log.debug("failed to parse x-sources header", e);
			return Collections.emptyList();
		}
	}
}
