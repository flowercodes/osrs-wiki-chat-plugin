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
			"Cite sources inline using the EXACT page titles from the context above, wrapped in square brackets, e.g. [Dragon scimitar].",
			"Only cite a page if you used substantive information from it in your answer. Do NOT cite pages that are merely tangentially related, that just happen to mention a keyword, or that you ignored.",
			"If none of the provided pages are substantively useful, say so plainly and cite nothing.",
			"Be concise. Use short paragraphs or bullet lists when appropriate.");
	}

	static String userPrompt(String question, List<Source> chunks, List<String> chunkTexts)
	{
		StringBuilder sb = new StringBuilder("OSRS Wiki context follows. Each block is a separate wiki page. ")
			.append("When citing a source, use its exact title in square brackets, e.g. [Dragon scimitar]. ")
			.append("Do not use numeric citations like [1] — use the page title.\n\n");
		for (int i = 0; i < chunks.size(); i++)
		{
			Source src = chunks.get(i);
			String text = chunkTexts.get(i);
			sb.append("=== Page: ").append(src.getTitle()).append(" ===\n")
				.append("URL: ").append(src.getUrl()).append("\n\n")
				.append(text).append("\n\n");
		}
		sb.append("User question: ").append(question);
		return sb.toString();
	}
}
