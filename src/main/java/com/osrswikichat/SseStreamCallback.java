package com.osrswikichat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
abstract class SseStreamCallback implements Callback
{
	private final LLMClient.Listener listener;
	final Gson gson;
	private final String providerName;

	SseStreamCallback(LLMClient.Listener listener, Gson gson, String providerName)
	{
		this.listener = listener;
		this.gson = gson;
		this.providerName = providerName;
	}

	abstract String extractDelta(JsonObject event);

	@Override
	public void onFailure(Call call, IOException e)
	{
		if (call.isCanceled())
		{
			return;
		}
		listener.onError("Network error: " + e.getMessage());
	}

	@Override
	public void onResponse(Call call, Response response) throws IOException
	{
		try (Response r = response)
		{
			if (!r.isSuccessful())
			{
				listener.onError(friendlyError(r));
				return;
			}
			ResponseBody respBody = r.body();
			if (respBody == null)
			{
				listener.onError("Empty response from " + providerName);
				return;
			}
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(respBody.byteStream(), StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (call.isCanceled())
					{
						return;
					}
					if (!line.startsWith("data:"))
					{
						continue;
					}
					String payload = line.substring(5).trim();
					if (payload.isEmpty() || "[DONE]".equals(payload))
					{
						continue;
					}
					try
					{
						JsonObject event = gson.fromJson(payload, JsonObject.class);
						String delta = extractDelta(event);
						if (delta != null && !delta.isEmpty())
						{
							listener.onChunk(delta);
						}
					}
					catch (Exception ignored)
					{
						// skip malformed SSE line
					}
				}
			}
			listener.onDone();
		}
		catch (IOException e)
		{
			if (!call.isCanceled())
			{
				listener.onError("Read failed: " + e.getMessage());
			}
		}
	}

	private String friendlyError(Response r) throws IOException
	{
		ResponseBody body = r.body();
		String preview = body != null ? body.string() : "";
		if (preview.length() > 200)
		{
			preview = preview.substring(0, 200);
		}
		switch (r.code())
		{
			case 401:
			case 403:
				return "API key rejected by " + providerName
					+ ". Check the key in ⚙ settings.";
			case 429:
				return providerName + " rate limit hit. Wait a minute or paste a different key.";
			default:
				return providerName + " HTTP " + r.code() + ": " + preview;
		}
	}
}
