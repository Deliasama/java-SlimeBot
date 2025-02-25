package de.slimecloud.slimeball.features.moderation;

import de.cyklon.jevent.EventHandler;
import de.cyklon.jevent.JEvent;
import de.slimecloud.slimeball.main.SlimeBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Optional;

public class TimeoutListener extends ListenerAdapter {
	private final SlimeBot bot;

	public TimeoutListener(@NotNull SlimeBot bot) {
		this.bot = bot;
		JEvent.getDefaultManager().registerListener(this);
	}

	@Override
	public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
		if (event.getNewTimeOutEnd() == null) return;

		//Discord doesn't provide the team member to use, so we fetch it from the audit logs
		event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).forEachAsync(entry -> {
			if (entry.getTargetIdLong() != event.getUser().getIdLong()) return true; //Continue non-matches

			if (bot.getConfig().getTimeoutIgnore().contains(entry.getUserIdLong())) return false;

			String reason = Optional.ofNullable(entry.getReason()).orElse("N/A");

			//Call event and remove timout if canceled
			if (new UserTimeoutedEvent(event.getMember(), entry.getUser(), reason, event.getNewTimeOutEnd().toInstant()).callEvent())
				event.getMember().removeTimeout().queue();

			return false;
		});
	}

	@EventHandler
	public void onTimeout(@NotNull UserTimeoutedEvent event) {
		//Send private message information
		event.getTarget().getUser().openPrivateChannel().flatMap(channel -> channel.sendMessageEmbeds(
				new EmbedBuilder()
						.setTitle("Du wurdest getimeouted")
						.setColor(bot.getColor(event.getTarget().getGuild()))
						.setTimestamp(Instant.now())
						.setDescription("Du wurdest auf dem SlimeCloud Discord getimeouted")
						.addField("Teammitglied", event.getTeam().getAsMention(), true)
						.addField("Endet", TimeFormat.RELATIVE.format(event.getEnd()), true)
						.addField("Grund", event.getReason(), false)
						.build()
		)).queue();

		//Send log for team members
		bot.loadGuild(event.getTarget().getGuild()).getPunishmentChannel().ifPresent(channel -> channel.sendMessageEmbeds(
				new EmbedBuilder()
						.setTitle("\uD83D\uDE34  **" + event.getTarget().getEffectiveName() + "** wurde getimeouted")
						.setColor(bot.getColor(event.getTarget().getGuild()))
						.setTimestamp(Instant.now())
						.addField("Nutzer", event.getTarget().getAsMention(), true)
						.addField("Teammitglied", event.getTeam().getAsMention(), true)
						.addField("Endet", TimeFormat.RELATIVE.format(event.getEnd()), true)
						.addField("Grund", event.getReason(), false)
						.build()
		).queue());
	}
}
