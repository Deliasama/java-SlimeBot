package de.slimecloud.slimeball.features.birthday;

import de.mineking.discordutils.list.ListContext;
import de.mineking.discordutils.list.Listable;
import de.mineking.discordutils.ui.MessageMenu;
import de.mineking.discordutils.ui.state.DataState;
import de.mineking.javautils.database.Table;
import de.mineking.javautils.database.Where;
import de.slimecloud.slimeball.features.birthday.event.BirthdayRemoveEvent;
import de.slimecloud.slimeball.features.birthday.event.BirthdaySetEvent;
import de.slimecloud.slimeball.main.SlimeBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BirthdayTable extends Table<Birthday>, Listable<Birthday> {
	default void delete(@NotNull Member member) {
		if (new BirthdayRemoveEvent(member).callEvent()) return;

		delete(Where.allOf(
				Where.equals("guild", member.getGuild()),
				Where.equals("user", member)
		));
	}

	@NotNull
	default Birthday save(@NotNull Member member, @NotNull Instant date) {
		Birthday birthday = new Birthday(getManager().getData("bot"), member.getGuild(), member, date);
		if (new BirthdaySetEvent(member, birthday).callEvent()) return birthday;

		return insert(birthday);
	}

	@NotNull
	default Optional<Birthday> get(@NotNull Member user) {
		return selectOne(Where.allOf(
				Where.equals("guild", user.getGuild()),
				Where.equals("user", user)
		));
	}

	@NotNull
	default List<Birthday> getAll() {
		return selectAll();
	}

	/*
	Listable implementation
	 */
	@Override
	default int entriesPerPage() {
		return 10;
	}

	@NotNull
	@Override
	default EmbedBuilder createEmbed(@NotNull DataState<MessageMenu> state, @NotNull ListContext<Birthday> context) {
		EmbedBuilder builder = new EmbedBuilder()
				.setTitle("Geburtstage")
				.setColor(getManager().<SlimeBot>getData("bot").getColor(state.getEvent().getGuild()))
				.setTimestamp(Instant.now());

		if (context.entries().isEmpty()) builder.setDescription("*Keine Einträge*");
		else builder.setFooter("Insgesamt " + context.entries().size() + " eingetragene Geburtstage");

		return builder;
	}

	@NotNull
	@Override
	default List<Birthday> getEntries(@NotNull DataState<MessageMenu> state, @NotNull ListContext<Birthday> context) {
		return selectAll().stream()
				.sorted()
				.toList();
	}
}
