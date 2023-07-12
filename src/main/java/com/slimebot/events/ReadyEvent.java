package com.slimebot.events;


import com.slimebot.alerts.spotify.SpotifyListener;
import com.slimebot.main.DatabaseField;
import com.slimebot.main.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

public class ReadyEvent extends ListenerAdapter {
	@Override
	public void onReady(@NotNull net.dv8tion.jda.api.events.session.ReadyEvent event) {
		try {
			Main.spotify.register();
		} catch(Exception e) {
			SpotifyListener.logger.error("Konnte spotify listener nicht starten", e);
		}

		for(Guild guild : Main.jdaInstance.getGuilds()) {
			MessageChannel channel = Main.database.getChannel(guild, DatabaseField.LOG_CHANNEL);

			if(channel == null) continue;

			channel.sendMessageEmbeds(
					new EmbedBuilder()
							.setTitle("Bot wurde gestartet")
							.setDescription("Der Bot hat sich mit der DiscordAPI (neu-) verbunden")
							.setColor(Main.database.getColor(guild))
							.setTimestamp(Instant.now())
							.build()
			).queue();
		}
	}
}
