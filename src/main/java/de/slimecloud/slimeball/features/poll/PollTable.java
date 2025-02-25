package de.slimecloud.slimeball.features.poll;

import de.mineking.javautils.database.Table;
import de.mineking.javautils.database.Where;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.stream.Collectors;

public interface PollTable extends Table<Poll> {
	@NotNull
	default Poll createPoll(long message, int max, String[] choices) {
		return insert(new Poll(getManager().getData("bot"), message, max, Arrays.stream(choices).collect(Collectors.toMap(c -> c, x -> new ArrayList<>(), (x, y) -> y, LinkedHashMap::new))));
	}

	default void delete(long message) {
		delete(Where.equals("id", message));
	}

	@NotNull
	default Optional<Poll> getPoll(long message) {
		return selectOne(Where.equals("id", message));
	}
}
