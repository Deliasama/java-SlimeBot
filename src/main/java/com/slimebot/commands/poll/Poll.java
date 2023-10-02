package com.slimebot.commands.poll;

import com.slimebot.database.DataClass;
import com.slimebot.database.Key;
import com.slimebot.main.Main;
import com.slimebot.util.Util;

import java.util.*;

public class Poll extends DataClass {
	@Key
	private final long id;

	private String json;

	private transient List<String>[] values;

	private Poll() {
		id = 0;
	}

	public Poll(long id, int options) {
		this.id = id;
		this.values = Util.createListArray(options);
		generateJson();
	}


	public static Optional<Poll> getPoll(long id) {
		return load(Poll::new, Map.of("id", id));
	}

	@Override
	public synchronized Poll save() {
		generateJson();
		super.save();
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void finishedLoading() {
		this.values = Main.gson.fromJson(json, List[].class);
	}

	private void generateJson() {
		this.json = Main.gson.toJson(values);
	}


	/**
	 * @return true if the member has already voted and was removed now
	 */
	private boolean remove(long member) {
		for (List<String> value : values)
			if (value.remove(Long.toString(member))) return true;

		return false;
	}

	public enum Type {
		REMOVED,
		SET,
		REMOVED_SET
	}

	public Type set(int option, long member) {
		if (values[option].remove(Long.toString(member))) return Type.REMOVED;
		else {
			boolean flag = remove(member);
			values[option].add(Long.toString(member));

			return flag ? Type.REMOVED_SET : Type.SET;
		}
	}

	public List<String> getOption(int option) {
		return values[option];
	}

	public List<String>[] getOptions() {
		return values;
	}

	public List<Long> getAll() {
		return Arrays.stream(values)
				.flatMap(Collection::stream)
				.map(Long::parseLong)
				.toList();
	}
}
