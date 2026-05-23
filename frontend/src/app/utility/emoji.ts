import {CompactEmoji} from "emojibase";
import emojisEn1 from "emojibase-data/en/compact.json";
import emojisEn2 from "emojibase-data/en-gb/compact.json";
import emojisCn from "emojibase-data/zh/compact.json";
import emojisZh from "emojibase-data/zh-hant/compact.json";

const emojiGroups = [emojisEn1, emojisEn2, emojisCn, emojisZh];
export const emojiForLabel: Record<string, CompactEmoji> = {};
const tagsForLabel: Record<string, string[]> = {};
const tempEmojisForGroup: CompactEmoji[][] = [];
const ungroupedEmojis: CompactEmoji[] = [];

emojiGroups[0].forEach(compactEmoji => {
	emojiForLabel[compactEmoji.label] = compactEmoji;
	if (compactEmoji.group !== undefined) {
		pushToIndex(tempEmojisForGroup, compactEmoji.group, [], []);
		tempEmojisForGroup[compactEmoji.group].push(compactEmoji);
	} else {
		ungroupedEmojis.push(compactEmoji);
	}
});

export const emojisForGroup: CompactEmoji[][] = [...tempEmojisForGroup.filter(emojis => emojis.length > 0), ungroupedEmojis];

emojiGroups.forEach(compactEmojis => compactEmojis.forEach(compactEmoji => {
	tagsForLabel[compactEmoji.label] = [compactEmoji.label];

	if (compactEmoji.group && compactEmoji.order) {
		if (typeof compactEmoji.emoticon === "string") {
			tagsForLabel[compactEmoji.label].push(compactEmoji.emoticon);
		} else if (typeof compactEmoji.emoticon === "object") {
			compactEmoji.emoticon.forEach(emoticon => tagsForLabel[compactEmoji.label].push(emoticon));
		}

		if (compactEmoji.tags) {
			compactEmoji.tags.forEach(tag => tagsForLabel[compactEmoji.label].push(tag));
		}
	}
}));

export function emojiMatchesTag(label: string, searchTerm: string) {
	const tags = tagsForLabel[label];
	if (tags) {
		return tags.some(tag => tag.toLowerCase().includes(searchTerm.toLowerCase()));
	} else {
		return false;
	}
}

function pushToIndex<T>(array: T[], index: number, value: T, defaultValue: T) {
	if (array.length <= index) {
		while (array.length <= index) {
			array.push(defaultValue);
		}
		array[index] = value;
	}
}
