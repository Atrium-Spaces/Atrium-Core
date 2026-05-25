import {CompactEmoji} from "emojibase";
import emojisEn1 from "emojibase-data/en/compact.json";
import emojisEn2 from "emojibase-data/en-gb/compact.json";
import emojisCn from "emojibase-data/zh/compact.json";
import emojisZh from "emojibase-data/zh-hant/compact.json";
import {setIfUndefined} from "./utilities";

const emojiGroups = [emojisEn1, emojisEn2, emojisCn, emojisZh];
export const emojiForHexCode: Record<string, CompactEmoji> = {};
const tagsForHexCode: Record<string, Set<string>> = {};
const tempEmojisForGroup: CompactEmoji[][] = [];
const ungroupedEmojis: CompactEmoji[] = [];

emojiGroups[0].forEach(compactEmoji => {
	emojiForHexCode[compactEmoji.hexcode] = compactEmoji;
	if (compactEmoji.group !== undefined) {
		pushToIndex(tempEmojisForGroup, compactEmoji.group, [], []);
		tempEmojisForGroup[compactEmoji.group].push(compactEmoji);
	} else {
		ungroupedEmojis.push(compactEmoji);
	}
});

export const emojisForGroup: CompactEmoji[][] = [...tempEmojisForGroup.filter(emojis => emojis.length > 0), ungroupedEmojis];

emojiGroups.forEach(compactEmojis => compactEmojis.forEach(compactEmoji => {
	setIfUndefined(tagsForHexCode, compactEmoji.hexcode, () => new Set());
	tagsForHexCode[compactEmoji.hexcode].add(compactEmoji.label);

	if (typeof compactEmoji.emoticon === "string") {
		tagsForHexCode[compactEmoji.hexcode].add(compactEmoji.emoticon);
	} else if (typeof compactEmoji.emoticon === "object") {
		compactEmoji.emoticon.forEach(emoticon => tagsForHexCode[compactEmoji.hexcode].add(emoticon));
	}

	if (compactEmoji.tags) {
		compactEmoji.tags.forEach(tag => tagsForHexCode[compactEmoji.hexcode].add(tag));
	}
}));

export function emojiMatchesHexCode(hexCode: string, searchTerm: string) {
	const tags = tagsForHexCode[hexCode];
	if (tags) {
		return [...tags].some(tag => tag.toLowerCase().includes(searchTerm.toLowerCase()));
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
