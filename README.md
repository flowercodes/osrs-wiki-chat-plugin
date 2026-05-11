# Wiki Chat

Ask any question about Old School RuneScape and get cited answers from the OSRS Wiki, right inside your RuneLite client.

## Features

- **Chat panel** — a side panel where you can ask anything about OSRS in plain English. Answers stream in word-by-word and cite the wiki pages they came from.
- **Right-click "Ask AI"** — right-click any item, NPC, or object that has an Examine option to get an "Ask AI" entry that pre-fills a question about it.
- **Hybrid retrieval** — every question searches both a pre-indexed vector store (~2,000 high-value OSRS wiki pages: quests, bosses, slayer monsters, minigames, skills, achievements, pets) **and** the live OSRS Wiki via MediaWiki search, so even niche pages are covered.
- **OSRS-only** — the assistant is explicitly anchored to the OSRS Wiki. RS3 questions and content are refused.
- **Bring your own key** — uses your personal Gemini, OpenAI, or Anthropic (Claude) API key. Nothing shared, no rate-limit collisions with other users.

## Setup

1. Install **Wiki Chat** from the RuneLite Plugin Hub.
2. Click the Wiki Chat icon in the RuneLite side toolbar to open the panel.
3. Click the ⚙ in the panel header.
4. Choose a provider, click the "Get a key" link, follow the signup, and paste your key.
5. Click **Save**.

### Provider options

| Provider | Cost | Notes |
|---|---|---|
| **Gemini** | Free tier: 1,500 questions/day | Recommended for most users — no credit card required. |
| **OpenAI** | ~$0.001 per question (GPT-4o-mini) | Requires credit card. |
| **Anthropic (Claude)** | ~$0.006 per question (Claude Haiku) | Requires credit card. Higher quality answers. |

## Privacy

This plugin sends data to a third-party server (the Wiki Chat backend on Cloudflare Workers) and from there to your selected LLM provider.

**What gets sent:**
- The text of your typed question
- Your API key (forwarded to your chosen LLM provider only, never stored on the backend)
- Your IP address (visible to the backend by virtue of HTTP)

**What does NOT get sent:**
- Your username, character name, or any account information
- Your stats, inventory, location, or any in-game state
- Any data when the plugin is disabled

You can self-host the backend by deploying the Worker yourself and changing the **Backend URL** in plugin config.

## How it works

```
You type a question
       │
       ▼
RuneLite plugin
       │ HTTPS
       ▼
Cloudflare Worker
  ├─ Vector search ~2,000 indexed OSRS wiki pages
  ├─ Live MediaWiki search for fresh content
  └─ Forwards question + retrieved context + your API key
       │
       ▼
Gemini / OpenAI / Anthropic
       │
       ▼
Streamed answer back into the panel
```

## Build from source

Requires Java 11+ (Java 17 recommended on modern macOS).

```bash
./gradlew run
```

Launches a RuneLite developer client with the plugin loaded.

## License

[BSD 2-Clause](LICENSE)
