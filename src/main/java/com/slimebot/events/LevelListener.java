package com.slimebot.events;

import com.slimebot.level.Level;
import com.slimebot.main.Main;
import com.slimebot.main.config.LevelConfig;
import com.slimebot.main.config.guild.GuildConfig;
import com.slimebot.util.MathUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LevelListener extends ListenerAdapter {
	private final LevelConfig config = Main.config.level;

	private final Map<Long, Long> messageTimeout = new HashMap<>();
	private final Map<Long, Long> voiceUsers = new HashMap<>();

	@Override
	public void onReady(@NotNull ReadyEvent event) {
		Main.jdaInstance.getVoiceChannels().forEach(this::updateChannel);

		Main.scheduleAtFixedRate(Main.config.level.voiceLevelingInterval, TimeUnit.SECONDS, () ->
				voiceUsers.forEach((user, guild) ->
						Level.getLevel(guild, user)
								.addXp(0, (int) (MathUtil.randomInt(config.minVoiceXP, config.maxVoiceXP) * GuildConfig.getConfig(guild).getLevelConfig()
										.map(config -> config.xpMultiplier)
										.orElse(1.0)
								))
								.save()
				)
		);
	}

	@Override
	public void onGuildBan(@NotNull GuildBanEvent event) {
		Level.getLevel(event.getGuild().getIdLong(), event.getUser().getIdLong())
				.setXp(0, 0)
				.save();
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		User author = event.getAuthor();

		if (!event.isFromGuild() || author.isBot() || isBlacklisted((ICategorizableChannel) event.getChannel())) return;

		if (messageTimeout.getOrDefault(author.getIdLong(), 0L) + config.messageCooldown >= System.currentTimeMillis())
			return;
		messageTimeout.put(author.getIdLong(), System.currentTimeMillis());

		double xp = MathUtil.randomInt(config.minMessageXP, config.maxMessageXP);

		for (String word : event.getMessage().getContentRaw().split(" ")) {
			if (word.length() >= config.minWordLength) {
				xp += MathUtil.randomDouble(config.minWordXP, config.maxWordXP);
			}
		}

		Level.getLevel(event.getMember())
				.addXp(0, (int) (MathUtil.round(xp) * GuildConfig.getConfig(event.getGuild()).getLevelConfig().map(config -> config.xpMultiplier).orElse(1.0)))
				.addMessages(1)
				.save();
	}

	@Override
	public void onGenericGuildVoice(GenericGuildVoiceEvent event) {
		if (event instanceof GuildVoiceUpdateEvent update) {
			if (update.getChannelLeft() != null && update.getChannelJoined() == null)
				voiceUsers.remove(event.getMember().getIdLong());
			updateChannel(update.getChannelLeft());
			updateChannel(update.getChannelJoined());
		} else {
			updateChannel(event.getVoiceState().getChannel());
		}
	}

	private void updateChannel(AudioChannel channel) {
		if (channel == null) return;

		List<Member> validMembers = channel.getMembers().stream()
				.filter(m -> !m.getUser().isBot())
				.filter(m -> !m.getVoiceState().isMuted())
				.toList();

		if (validMembers.size() >= 2 && !isBlacklisted(channel)) {
			validMembers.forEach(m -> voiceUsers.put(m.getIdLong(), channel.getGuild().getIdLong()));
		} else {
			channel.getMembers().forEach(m -> voiceUsers.remove(m.getIdLong()));
		}
	}

	private boolean isBlacklisted(GuildChannel channel) {
		List<Long> blacklist = GuildConfig.getConfig(channel.getGuild()).getOrCreateLevel().blacklist;
		//Check blocklist for channel,
		return blacklist.contains(channel.getIdLong())
				//If channel has category: Check category blacklist
				|| (channel instanceof ICategorizableChannel cc && blacklist.contains(cc.getParentCategoryIdLong()))
				//If channel is ThreadChannel check parent channel and parent channel category
				|| (channel instanceof ThreadChannel tc &&
				(
						blacklist.contains(tc.getParentChannel().getIdLong())
								|| (tc.getParentChannel() instanceof ICategorizableChannel tpc && blacklist.contains(tpc.getParentCategoryIdLong()))
				));
	}
}