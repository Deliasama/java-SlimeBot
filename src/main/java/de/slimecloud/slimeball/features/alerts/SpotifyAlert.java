package de.slimecloud.slimeball.features.alerts;

import com.neovisionaries.i18n.CountryCode;
import de.slimecloud.slimeball.config.SpotifyConfig;
import de.slimecloud.slimeball.main.SlimeBot;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jetbrains.annotations.NotNull;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.requests.data.AbstractDataPagingRequest;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
public class SpotifyAlert {
	private final SlimeBot bot;

	public SpotifyAlert(@NotNull SlimeBot bot) {
		this.bot = bot;
		bot.getDatabase().getDriver().useHandle(handle -> handle.createUpdate("create table if not exists spotify_known(id text)").execute());

		bot.getExecutor().scheduleAtFixedRate(() -> {
			try {
				check();
			} catch (Exception e) {
				logger.error("Failed to fetch spotify releases", e);
			}
		}, 0, 1, TimeUnit.HOURS);

		logger.info("Spotify listener started");
	}

	public void check() {
		//Read releases that were already published
		List<String> known = bot.getDatabase().getDriver().withHandle(handle -> handle.createQuery("select id from spotify_known").mapTo(String.class).list());
		List<String> newIds = new ArrayList<>();

		//CHeck for podcast episodes
		bot.getConfig().getSpotify().flatMap(SpotifyConfig::getPodcastConfig).ifPresent(config -> {
			config.artistIds().stream()
					.flatMap(id -> getLatestEntries(id, bot.getSpotify().getApi()::getShowEpisodes).stream())
					.filter(e -> !known.contains(e.getId()))
					.forEach(e -> {
						newIds.add(e.getId());
						broadcast(config.message(), SpotifyNotificationConfig::getPodcastChannel, e.getName(), e.getExternalUrls().get("spotify"));
					});
		});

		//Check for music releases
		bot.getConfig().getSpotify().flatMap(SpotifyConfig::getMusicConfig).ifPresent(config -> {
			config.artistIds().stream()
					.flatMap(id -> getLatestEntries(id, bot.getSpotify().getApi()::getArtistsAlbums).stream())
					.filter(e -> !known.contains(e.getId()))
					.forEach(e -> {
						newIds.add(e.getId());
						broadcast(config.message(), SpotifyNotificationConfig::getMusicChannel, e.getName(), e.getExternalUrls().get("spotify"));
					});

		});

		logger.info("Found {} new entries", newIds.size());

		//Mark newly published releases
		bot.getDatabase().getDriver().useHandle(handle -> {
			PreparedBatch update = handle.prepareBatch("insert into spotify_known values(:id)");
			newIds.forEach(id -> update.bind("id", id).add());
			update.execute();
		});
	}

	private <T, R extends AbstractDataPagingRequest.Builder<T, ?>> List<T> getLatestEntries(String id, Function<String, R> request) {
		try {
			Paging<T> albumSimplifiedPaging = request.apply(id).setQueryParameter("market", CountryCode.DE).limit(20).build().execute();

			if (albumSimplifiedPaging.getTotal() > 20)
				albumSimplifiedPaging = request.apply(id).setQueryParameter("market", CountryCode.DE).limit(20).offset(albumSimplifiedPaging.getTotal() - 20).build().execute();

			List<T> albums = Arrays.asList(albumSimplifiedPaging.getItems());

			//Return older episodes first
			Collections.reverse(albums);
			return albums;
		} catch (Exception e) {
			logger.error("Error fetching data from spotify", e);
			return Collections.emptyList();
		}
	}

	private void broadcast(String format, Function<SpotifyNotificationConfig, Optional<GuildMessageChannel>> channel, String name, String url) {
		for (Guild guild : bot.getJda().getGuilds()) {
			bot.loadGuild(guild).getSpotify().ifPresent(spotify ->
					channel.apply(spotify).ifPresent(ch -> {
						String notification = spotify.getRole()
								.map(Role::getAsMention)
								.orElse("");

						try {
							//Replace placeholders
							ch.sendMessage(format
									.replace("%notification%", notification)
									.replace("%name%", name)
									.replace("%url%", url)
							).queue(msg -> {
								msg.createThreadChannel("Unterhaltet euch über diese Folge!").queue();
								if (msg.getChannelType().equals(ChannelType.NEWS)) msg.crosspost().queue();
							});
						} catch (Exception e) {
							logger.error("Failed to broadcast for guild {}", guild.getName(), e);
						}
					})
			);
		}
	}
}
