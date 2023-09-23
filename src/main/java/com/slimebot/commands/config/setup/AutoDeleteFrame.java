package com.slimebot.commands.config.setup;

import com.slimebot.commands.config.ConfigCommand;
import com.slimebot.commands.config.engine.ConfigFieldType;
import com.slimebot.commands.config.setup.engine.CustomSetupFrame;
import com.slimebot.main.config.guild.AutoDeleteConfig;
import com.slimebot.main.config.guild.GuildConfig;
import de.mineking.discord.ui.Menu;
import de.mineking.discord.ui.MenuBase;
import de.mineking.discord.ui.MessageFrameBase;
import de.mineking.discord.ui.components.Component;
import de.mineking.discord.ui.components.ComponentRow;
import de.mineking.discord.ui.components.button.ButtonColor;
import de.mineking.discord.ui.components.button.FrameButton;
import de.mineking.discord.ui.components.button.ToggleButton;
import de.mineking.discord.ui.components.button.ToggleHolder;
import de.mineking.discord.ui.components.select.EntitySelectComponent;
import de.mineking.discord.ui.components.select.StringSelectComponent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.util.*;
import java.util.stream.Collectors;

public class AutoDeleteFrame extends CustomSetupFrame {
	public AutoDeleteFrame(Menu menu) {
		super("auto-delete", menu,
				ConfigFieldType.CHANNEL.getEmoji() + "Auto-Delete Kanäle",
				"In diesen Kanälen werden Nachrichten von Nicht-Moderatoren gelöscht, wenn sie bestimmte Bedingungen nicht erfüllen"
		);

		menu.addFrame("auto-delete-config", AutoDeleteConfigFrame::new);
	}

	@Override
	public Optional<String> getValue(GuildConfig config) {
		return config.getAutoDeleteConfig().map(AutoDeleteConfig::getAutoDeleteChannels).filter(m -> !m.isEmpty()).map(channels -> channels.entrySet().stream()
				.map(e -> e.getKey().getAsMention() + ": " + getFilterString(e.getValue()))
				.collect(Collectors.joining("\n"))
		);
	}

	@Override
	public Collection<ComponentRow> getComponents() {
		return Arrays.asList(
				new EntitySelectComponent("add", config -> config
						.setPlaceholder("Kanal hinzufügen")
						.setChannelTypes(ChannelType.TEXT, ChannelType.FORUM),
						EntitySelectMenu.SelectTarget.CHANNEL
				).addHandler((m, event) -> {
					m.putData("channel", event.getValues().get(0).getId());
					ConfigCommand.updateField(menu.getGuild(), config -> config.getOrCreateAutoDelete().autoDeleteChannels.put(event.getValues().get(0).getId(), EnumSet.noneOf(AutoDeleteConfig.Filter.class)));
					m.display("auto-delete-config");
				}),
				new StringSelectComponent("edit", config -> {
					config.setPlaceholder("Kanal bearbeiten");

					configureSelect(config);
				}).asDisabled(() -> GuildConfig.getConfig(menu.getGuild()).getAutoDeleteConfig().map(a -> a.autoDeleteChannels.isEmpty()).orElse(true)).addHandler((m, event) -> {
					m.putData("channel", event.getValues().get(0));
					m.display("auto-delete-config");
				}),
				new StringSelectComponent("remove", config -> {
					config.setPlaceholder("Kanal entfernen");

					configureSelect(config);
				}).asDisabled(() -> GuildConfig.getConfig(menu.getGuild()).getAutoDeleteConfig().map(a -> a.autoDeleteChannels.isEmpty()).orElse(true)).addHandler((m, event) -> {
					GuildConfig.getConfig(menu.getGuild()).getAutoDeleteConfig().ifPresent(a -> a.autoDeleteChannels.remove(event.getSelectedOptions().get(0).getValue()));
					m.update();
				}),
				new FrameButton(ButtonColor.GRAY, "Zurück", "main")
		);
	}

	private void configureSelect(StringSelectMenu.Builder config) {
		Map<GuildChannel, EnumSet<AutoDeleteConfig.Filter>> data = GuildConfig.getConfig(menu.getGuild()).getAutoDeleteConfig().map(AutoDeleteConfig::getAutoDeleteChannels).orElse(Collections.emptyMap());

		if (data.isEmpty()) config.addOption("---", "---");
		else config.addOptions(
				data.entrySet().stream()
						.map(e -> SelectOption.of(e.getKey().getName(), e.getKey().getId())
								.withDescription(getFilterString(e.getValue()))
						)
						.toList()
		);
	}

	private static String getFilterString(EnumSet<AutoDeleteConfig.Filter> filters) {
		return filters.isEmpty()
				? "Keine Nachrichten erlaubt"
				: filters.stream().map(AutoDeleteConfig.Filter::getName).collect(Collectors.joining(","));
	}

	public static class AutoDeleteConfigFrame extends MessageFrameBase {
		public AutoDeleteConfigFrame(Menu menu) {
			super(menu);
		}

		@Override
		public MessageEmbed getEmbed() {
			return new EmbedBuilder()
					.setColor(GuildConfig.getColor(menu.getGuild()))
					.setTitle("Auto-Delete konfiguration")
					.setDescription("Konfiguriere welche Arten von Nachrichten in <#" + menu.getData("channel", String.class) + "> erlaubt sind, alle anderen werden gelöscht!")
					.build();
		}

		@Override
		public Collection<ComponentRow> getComponents() {
			List<Component<?>> components = new ArrayList<>();

			components.addAll(
					Arrays.stream(AutoDeleteConfig.Filter.values())
							.map(f -> new ToggleButton(f.toString(), new ToggleHolder() {
								@Override
								public void setState(boolean state, MenuBase menu, ButtonInteractionEvent event) {
									ConfigCommand.updateField(menu.getGuild(), c -> c.getAutoDeleteConfig().ifPresent(a -> {
										String id = menu.getData("channel", String.class);
										if (a.autoDeleteChannels.containsKey(id)) {
											a.autoDeleteChannels.get(id).remove(f);
											if (state) a.autoDeleteChannels.get(id).add(f);
										}
									}));
								}

								@Override
								public boolean getState(MenuBase menu) {
									return GuildConfig.getConfig(menu.getGuild()).getAutoDeleteConfig().map(a -> {
										String id = menu.getData("channel", String.class);
										return a.autoDeleteChannels.containsKey(id) && a.autoDeleteChannels.get(id).contains(f);
									}).orElse(false);
								}
							}, ToggleButton.redGreen, f.getName()))
							.toList()
			);

			components.add(new FrameButton(ButtonColor.GRAY, "Zurück", "auto-delete"));

			return ComponentRow.build(components);
		}
	}
}
