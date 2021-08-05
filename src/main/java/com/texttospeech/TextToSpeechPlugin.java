package com.texttospeech;

import com.google.inject.Provides;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import lombok.extern.slf4j.Slf4j;
import marytts.LocalMaryInterface;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.*;

import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Text-To-Speech"
)
public class TextToSpeechPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private TextToSpeechConfig config;

	private final Random random = new Random();
	private LocalMaryInterface mary;
	private final List<String> femaleVoices = Arrays.asList(
			"cmu-slt-hsmm",
			"dfki-poppy-hsmm",
			"dfki-prudence-hsmm"
	);
	private final List<String> maleVoices = Arrays.asList(
			"cmu-bdl-hsmm",
			"cmu-rms-hsmm",
			"dfki-obadiah-hsmm",
			"dfki-spike-hsmm"
	);

	@Override
	protected void startUp() throws Exception
	{
		mary = new LocalMaryInterface();
	}

	@Subscribe(priority = -2) // Run after ChatMessageManager.
	public void onChatMessage(ChatMessage chatMessage)
	{
		final MessageNode message = chatMessage.getMessageNode();
		if (!message.getType().equals(ChatMessageType.PUBLICCHAT)) return;
		String text = message.getValue();
		String senderName = Text.toJagexName(message.getName());
		for(Player player : client.getPlayers()) {
			String playerName = player.getName();
			if (playerName == null) continue;
			playerName = Text.toJagexName(playerName);
			if (!playerName.equals(senderName)) continue;
			boolean isFemale = player.getPlayerComposition().isFemale();
			textToSpeech(text, playerName, isFemale);
		}
	}

	private void textToSpeech(String text, String name, boolean isFemale) {
		int voiceIndex = isFemale ? name.hashCode() % femaleVoices.size() : name.hashCode() % maleVoices.size();
		String voice = isFemale ? femaleVoices.get(voiceIndex) : maleVoices.get(voiceIndex);
		float tractScaler = (name.hashCode() % 50) / 50.0f + 0.70f; // Will be between [0.7, 1.2]
		float f0Scale = (name.hashCode() % 200) / 200.0f + 1.00f; // Will be between [1.0, 3.0]

		mary.setVoice(voice);
		mary.setAudioEffects(
			"TractScaler(amount:"+tractScaler+";)+" + 	// TractScaler [0.25, 4.0]: deeper to higher (default = 1.0)
			"F0Scale(amount:"+f0Scale+";)"			// F0Scale [0.0, 3.0]: monotonic to variable pitch (default = 1.0)
		);

		try {
			AudioInputStream audio = mary.generateAudio(text);
			Clip clip = AudioSystem.getClip();
			clip.open(audio);
			clip.start();
		} catch (Exception ignored) {}
	}

	@Override
	protected void shutDown() throws Exception
	{

	}

	@Provides
	TextToSpeechConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TextToSpeechConfig.class);
	}
}
