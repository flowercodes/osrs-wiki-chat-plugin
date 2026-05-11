package com.osrswikichat;

import java.util.List;

final class PromptBuilder
{
	private PromptBuilder()
	{
	}

	static String systemPrompt()
	{
		return String.join(" ",
			"You are an expert assistant for Old School RuneScape (OSRS) ONLY.",
			"OSRS is a separate game from RuneScape 3 (RS3). They have different items, monsters, mechanics, and content. NEVER mix RS3 information into your answers.",
			"Your ONLY source of truth is the OSRS wiki context provided below (from oldschool.runescape.wiki).",
			"Do not use any prior knowledge of RuneScape from your training data — if it isn't in the wiki context, you don't know it.",
			"If the context is insufficient to answer, say so plainly and suggest the user check the relevant wiki page — do not invent facts or fall back on RS3 knowledge.",
			"If the user asks about something that only exists in RS3 (e.g. RS3-only skills, bosses, or items), clarify that this assistant only covers OSRS.",
			"Cite sources inline using the page titles in brackets, e.g. [Dragon scimitar].",
			"Be concise. Use short paragraphs or bullet lists when appropriate.");
	}

	static String userPrompt(String question, List<Source> chunks, List<String> chunkTexts)
	{
		StringBuilder sb = new StringBuilder("Wiki context:\n\n");
		for (int i = 0; i < chunks.size(); i++)
		{
			Source src = chunks.get(i);
			String text = chunkTexts.get(i);
			sb.append('[').append(i + 1).append("] ")
				.append(src.getTitle()).append(" (").append(src.getUrl()).append(")\n")
				.append(text).append("\n\n---\n\n");
		}
		sb.append("User question: ").append(question);
		return sb.toString();
	}
}
