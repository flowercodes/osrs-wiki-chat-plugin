package com.osrswikichat;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.inject.Inject;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;
import okhttp3.Call;

class WikiChatPanel extends PluginPanel
{
	private final WikiChatClient client;
	private final ConfigManager configManager;
	private final WikiChatConfig config;

	private static final int MIN_INPUT_ROWS = 1;
	private static final int MAX_INPUT_ROWS = 6;

	private final ScrollablePanel messageList = new ScrollablePanel();
	private final JScrollPane scrollPane;
	private final JTextArea input = new JTextArea(MIN_INPUT_ROWS, 1);
	private final JScrollPane inputScroll;
	private final JButton sendButton = new JButton("Ask");
	private final JLabel statusLabel = new JLabel(" ");

	private final JPanel settingsPanel = new JPanel();
	private final JComboBox<Provider> providerCombo = new JComboBox<>(Provider.values());
	private final JPasswordField apiKeyField = new JPasswordField();
	private final JLabel settingsStatus = new JLabel(" ");
	private final JTextArea providerInfo = new JTextArea();
	private final JLabel getKeyLink = new JLabel();

	private Call inflight;
	private JTextArea streamingTarget;
	private JPanel streamingBubble;

	@Inject
	WikiChatPanel(WikiChatClient client, ConfigManager configManager, WikiChatConfig config)
	{
		super(false);
		this.client = client;
		this.configManager = configManager;
		this.config = config;

		setLayout(new BorderLayout(0, 6));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
		messageList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		messageList.setBorder(new EmptyBorder(0, 0, 0, 0));

		scrollPane = new JScrollPane(messageList,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Wiki Chat");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JButton settingsToggle = new JButton("⚙");
		settingsToggle.setMargin(new java.awt.Insets(0, 6, 0, 6));
		settingsToggle.setToolTipText("Provider & API key");
		settingsToggle.setFocusPainted(false);
		settingsToggle.addActionListener(e -> toggleSettings());

		JPanel headerRight = new JPanel(new BorderLayout(4, 0));
		headerRight.setOpaque(false);
		headerRight.add(statusLabel, BorderLayout.CENTER);
		headerRight.add(settingsToggle, BorderLayout.EAST);

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);
		header.add(title, BorderLayout.WEST);
		header.add(headerRight, BorderLayout.EAST);

		buildSettingsPanel();

		JPanel northContainer = new JPanel(new BorderLayout(0, 6));
		northContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		northContainer.add(header, BorderLayout.NORTH);
		northContainer.add(settingsPanel, BorderLayout.CENTER);

		input.setLineWrap(true);
		input.setWrapStyleWord(true);
		input.setFont(FontManager.getRunescapeFont());
		input.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
		input.setForeground(Color.WHITE);
		input.setCaretColor(Color.WHITE);
		input.setBorder(new EmptyBorder(4, 6, 4, 6));
		input.setToolTipText("Ask anything about OSRS — Enter to send, Shift+Enter for newline");

		inputScroll = new JScrollPane(input,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		inputScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		inputScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);

		bindEnterToSubmit();
		input.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				SwingUtilities.invokeLater(WikiChatPanel.this::adjustInputHeight);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				SwingUtilities.invokeLater(WikiChatPanel.this::adjustInputHeight);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				SwingUtilities.invokeLater(WikiChatPanel.this::adjustInputHeight);
			}
		});

		JPanel inputRow = new JPanel(new BorderLayout(4, 0));
		inputRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputRow.add(inputScroll, BorderLayout.CENTER);
		inputRow.add(sendButton, BorderLayout.EAST);

		add(northContainer, BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(inputRow, BorderLayout.SOUTH);

		sendButton.addActionListener(e -> submit());

		adjustInputHeight();

		String key = config.userApiKey();
		if (key == null || key.trim().isEmpty())
		{
			settingsPanel.setVisible(true);
			appendAssistant(
				"Welcome! To get started, pick a provider above and paste an API key, then click Save. " +
					"Click the \"Get a key\" link for free/cheap signup options.",
				null);
		}
		else
		{
			appendAssistant(
				"Ask me anything about Old School RuneScape. I'll answer using the OSRS wiki and cite my sources.",
				null);
		}
	}

	private void submit()
	{
		String question = input.getText().trim();
		if (question.isEmpty())
		{
			return;
		}

		String key = config.userApiKey();
		if (key == null || key.trim().isEmpty())
		{
			input.setText("");
			appendUser(question);
			appendAssistant(
				"No API key set. Click the ⚙ button to choose a provider and paste a key.",
				null);
			settingsPanel.setVisible(true);
			revalidate();
			revalidateAndScroll();
			return;
		}

		if (inflight != null && !inflight.isCanceled())
		{
			inflight.cancel();
		}

		input.setText("");
		appendUser(question);

		streamingBubble = makeBubble(false);
		streamingTarget = (JTextArea) streamingBubble.getClientProperty("text");
		messageList.add(streamingBubble);
		messageList.add(Box.createVerticalStrut(6));
		revalidateAndScroll();

		setBusy(true);
		inflight = client.ask(question, new WikiChatClient.Listener()
		{
			@Override
			public void onChunk(String text)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (streamingTarget != null)
					{
						streamingTarget.append(text);
						revalidateAndScroll();
					}
				});
			}

			@Override
			public void onDone(List<Source> sources)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (streamingBubble != null && sources != null && !sources.isEmpty())
					{
						streamingBubble.add(makeSourcesPanel(sources), BorderLayout.SOUTH);
					}
					streamingBubble = null;
					streamingTarget = null;
					setBusy(false);
					revalidateAndScroll();
				});
			}

			@Override
			public void onError(String message)
			{
				SwingUtilities.invokeLater(() ->
				{
					if (streamingTarget != null)
					{
						streamingTarget.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
						streamingTarget.setText("Error: " + message);
					}
					streamingBubble = null;
					streamingTarget = null;
					setBusy(false);
					revalidateAndScroll();
				});
			}
		});
	}

	private void appendUser(String text)
	{
		JPanel bubble = makeBubble(true);
		((JTextArea) bubble.getClientProperty("text")).setText(text);
		messageList.add(bubble);
		messageList.add(Box.createVerticalStrut(6));
	}

	private void appendAssistant(String text, List<Source> sources)
	{
		JPanel bubble = makeBubble(false);
		((JTextArea) bubble.getClientProperty("text")).setText(text);
		if (sources != null && !sources.isEmpty())
		{
			bubble.add(makeSourcesPanel(sources), BorderLayout.SOUTH);
		}
		messageList.add(bubble);
		messageList.add(Box.createVerticalStrut(6));
	}

	private JPanel makeBubble(boolean isUser)
	{
		JPanel bubble = new JPanel(new BorderLayout(0, 2))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		bubble.setBackground(isUser
			? ColorScheme.DARKER_GRAY_HOVER_COLOR
			: ColorScheme.DARKER_GRAY_COLOR);
		bubble.setBorder(new EmptyBorder(6, 8, 6, 8));
		bubble.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel role = new JLabel(isUser ? "You" : "Wiki Chat");
		role.setFont(FontManager.getRunescapeSmallFont());
		role.setForeground(isUser ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);

		JTextArea body = new WrappingTextArea();
		body.setEditable(false);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		body.setOpaque(false);
		body.setForeground(Color.WHITE);
		body.setFont(FontManager.getRunescapeFont());
		body.setBorder(null);

		JPanel content = new JPanel(new BorderLayout(0, 2));
		content.setOpaque(false);
		content.add(role, BorderLayout.NORTH);
		content.add(body, BorderLayout.CENTER);

		bubble.add(content, BorderLayout.CENTER);
		bubble.putClientProperty("text", body);
		bubble.putClientProperty("extra", content);
		return bubble;
	}

	private JPanel makeSourcesPanel(List<Source> sources)
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(4, 0, 0, 0));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel header = new JLabel("Sources:");
		header.setFont(FontManager.getRunescapeSmallFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(header);

		java.util.Set<String> seen = new java.util.HashSet<>();
		for (Source src : sources)
		{
			if (src == null || src.getTitle() == null || !seen.add(src.getTitle()))
			{
				continue;
			}
			panel.add(makeSourceLink(src));
		}
		return panel;
	}

	private JLabel makeSourceLink(Source src)
	{
		JLabel link = new JLabel(src.getTitle());
		link.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.PLAIN));
		link.setForeground(ColorScheme.BRAND_ORANGE);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText(src.getUrl());
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (src.getUrl() != null && !src.getUrl().isEmpty())
				{
					LinkBrowser.browse(src.getUrl());
				}
			}
		});
		return link;
	}

	private void setBusy(boolean busy)
	{
		sendButton.setEnabled(!busy);
		input.setEnabled(!busy);
		statusLabel.setText(busy ? "thinking..." : " ");
		if (!busy)
		{
			input.requestFocusInWindow();
		}
	}

	private void revalidateAndScroll()
	{
		messageList.revalidate();
		messageList.repaint();
		Timer t = new Timer(20, e -> scrollPane.getVerticalScrollBar()
			.setValue(scrollPane.getVerticalScrollBar().getMaximum()));
		t.setRepeats(false);
		t.start();
	}

	private void bindEnterToSubmit()
	{
		KeyStroke enter = KeyStroke.getKeyStroke("ENTER");
		KeyStroke shiftEnter = KeyStroke.getKeyStroke("shift ENTER");

		input.getInputMap(JComponent.WHEN_FOCUSED).put(enter, "submit");
		input.getActionMap().put("submit", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				submit();
			}
		});
		input.getInputMap(JComponent.WHEN_FOCUSED).put(shiftEnter, "insert-break");
	}

	private void adjustInputHeight()
	{
		int lineHeight = input.getFontMetrics(input.getFont()).getHeight();
		int padTop = input.getInsets().top;
		int padBottom = input.getInsets().bottom;
		int border = 2;

		int viewWidth = inputScroll.getViewport().getWidth();
		if (viewWidth > 0)
		{
			input.setSize(new Dimension(viewWidth, Short.MAX_VALUE));
		}

		int rowsByText;
		try
		{
			View root = input.getUI().getRootView(input);
			int rendered = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
			rowsByText = Math.max(1, (int) Math.round((double) rendered / lineHeight));
		}
		catch (Exception ex)
		{
			rowsByText = Math.max(1, input.getLineCount());
		}

		int rows = Math.max(MIN_INPUT_ROWS, Math.min(MAX_INPUT_ROWS, rowsByText));
		int newHeight = rows * lineHeight + padTop + padBottom + border;

		Dimension current = inputScroll.getPreferredSize();
		if (current == null || current.height != newHeight)
		{
			inputScroll.setPreferredSize(new Dimension(0, newHeight));
			inputScroll.revalidate();
			revalidate();
			repaint();
		}
	}

	void cancelInflight()
	{
		if (inflight != null && !inflight.isCanceled())
		{
			inflight.cancel();
			inflight = null;
		}
	}

	void prefillAndFocus(String text)
	{
		SwingUtilities.invokeLater(() ->
		{
			input.setText(text);
			input.setCaretPosition(text.length());
			adjustInputHeight();
			input.requestFocusInWindow();
		});
	}

	private void buildSettingsPanel()
	{
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		settingsPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
		settingsPanel.setVisible(false);

		providerCombo.setSelectedItem(config.provider());
		providerCombo.addActionListener(e -> updateProviderHelp());

		apiKeyField.setText(config.userApiKey() == null ? "" : config.userApiKey());
		apiKeyField.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
		apiKeyField.setForeground(Color.WHITE);
		apiKeyField.setCaretColor(Color.WHITE);
		apiKeyField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(2, 4, 2, 4)
		));
		bindClipboardShortcuts(apiKeyField);
		bindClipboardShortcuts(input);

		providerInfo.setEditable(false);
		providerInfo.setLineWrap(true);
		providerInfo.setWrapStyleWord(true);
		providerInfo.setOpaque(false);
		providerInfo.setFont(FontManager.getRunescapeSmallFont());
		providerInfo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		providerInfo.setBorder(null);
		providerInfo.setFocusable(false);

		getKeyLink.setFont(FontManager.getRunescapeSmallFont());
		getKeyLink.setForeground(ColorScheme.BRAND_ORANGE);
		getKeyLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		getKeyLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				Object sel = providerCombo.getSelectedItem();
				if (sel instanceof Provider)
				{
					LinkBrowser.browse(((Provider) sel).keyUrl());
				}
			}
		});

		JPanel providerRow = labelledRow("Provider:", providerCombo);
		JPanel keyRow = labelledRow("API key:", apiKeyField);

		settingsStatus.setFont(FontManager.getRunescapeSmallFont());
		settingsStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JButton saveButton = new JButton("Save");
		saveButton.setFocusPainted(false);
		saveButton.addActionListener(e -> saveSettings());

		JPanel saveRow = new JPanel(new BorderLayout(6, 0));
		saveRow.setOpaque(false);
		saveRow.add(settingsStatus, BorderLayout.CENTER);
		saveRow.add(saveButton, BorderLayout.EAST);

		settingsPanel.add(providerRow);
		settingsPanel.add(Box.createVerticalStrut(4));
		settingsPanel.add(leftAligned(providerInfo));
		settingsPanel.add(Box.createVerticalStrut(2));
		settingsPanel.add(leftAligned(getKeyLink));
		settingsPanel.add(Box.createVerticalStrut(8));
		settingsPanel.add(keyRow);
		settingsPanel.add(Box.createVerticalStrut(6));
		settingsPanel.add(saveRow);

		updateProviderHelp();
	}

	private JPanel leftAligned(JComponent c)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(c, BorderLayout.WEST);
		return row;
	}

	private void updateProviderHelp()
	{
		Object sel = providerCombo.getSelectedItem();
		if (sel instanceof Provider)
		{
			Provider p = (Provider) sel;
			providerInfo.setText(p.info());
			getKeyLink.setText("Get a " + p.toString() + " key →");
			getKeyLink.setToolTipText(p.keyUrl());
		}
	}

	private JPanel labelledRow(String labelText, JComponent field)
	{
		JLabel label = new JLabel(labelText);
		label.setForeground(Color.WHITE);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setPreferredSize(new Dimension(70, label.getPreferredSize().height));

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private void toggleSettings()
	{
		boolean show = !settingsPanel.isVisible();
		if (show)
		{
			providerCombo.setSelectedItem(config.provider());
			apiKeyField.setText(config.userApiKey() == null ? "" : config.userApiKey());
			settingsStatus.setText(" ");
		}
		settingsPanel.setVisible(show);
		revalidate();
		repaint();
	}

	private static void bindClipboardShortcuts(JTextComponent c)
	{
		int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		c.getInputMap(JComponent.WHEN_FOCUSED).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_V, mask), DefaultEditorKit.pasteAction);
		c.getInputMap(JComponent.WHEN_FOCUSED).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), DefaultEditorKit.copyAction);
		c.getInputMap(JComponent.WHEN_FOCUSED).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_X, mask), DefaultEditorKit.cutAction);
		c.getInputMap(JComponent.WHEN_FOCUSED).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_A, mask), DefaultEditorKit.selectAllAction);
	}

	private void saveSettings()
	{
		Object selected = providerCombo.getSelectedItem();
		Provider provider = selected instanceof Provider ? (Provider) selected : Provider.GEMINI;
		String key = new String(apiKeyField.getPassword()).trim();

		configManager.setConfiguration(WikiChatConfig.GROUP, "provider", provider);
		configManager.setConfiguration(WikiChatConfig.GROUP, "userApiKey", key);

		settingsStatus.setText("Saved");
		Timer t = new Timer(2000, e -> settingsStatus.setText(" "));
		t.setRepeats(false);
		t.start();
	}

	private static class ScrollablePanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 100;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static class WrappingTextArea extends JTextArea
	{
		@Override
		public Dimension getPreferredSize()
		{
			Container parent = getParent();
			if (parent != null)
			{
				int width = parent.getWidth();
				if (width > 0 && width != getWidth())
				{
					setSize(width, Short.MAX_VALUE);
				}
			}
			return super.getPreferredSize();
		}
	}
}
