import {ChangeDetectionStrategy, Component, effect, inject, model, signal} from "@angular/core";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";

import {CompactEmoji} from "emojibase";
import {TranslocoDirective} from "@jsverse/transloco";
import {ButtonModule} from "primeng/button";
import {DialogModule} from "primeng/dialog";
import {DividerModule} from "primeng/divider";
import {FloatLabel, FloatLabelModule} from "primeng/floatlabel";
import {IconFieldModule} from "primeng/iconfield";
import {InputIconModule} from "primeng/inputicon";
import {InputText, InputTextModule} from "primeng/inputtext";
import {ScrollerModule} from "primeng/scroller";

import {emojiMatchesTag, emojisForGroup} from "../../utility/emoji";
import {AuthenticationService} from "../../service/authentication.service";
import {RequestService} from "../../service/request.service";
import {ProfileComponent} from "../profile/profile.component";

@Component({
	selector: "app-edit-profile",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		DialogModule,
		FloatLabelModule,
		InputTextModule,
		IconFieldModule,
		InputIconModule,
		ScrollerModule,
		DividerModule,
		ButtonModule,
		FloatLabel,
		InputText,
		TranslocoDirective,
		ProfileComponent,
		ReactiveFormsModule,
		FormsModule,
	],
	templateUrl: "./edit-profile.component.html",
	styleUrl: "./edit-profile.component.scss",
})
export class EditProfileComponent {
	private readonly requestService = inject(RequestService);
	private readonly authenticationService = inject(AuthenticationService);

	dialogVisible = model.required<boolean>();
	protected nameInput = model("");
	protected searchInput = model("");
	protected readonly emojis = signal<CompactEmoji[][]>([]);
	protected readonly emojiButtonSize = 58;
	protected readonly scrollBarWidth = 14;
	protected readonly rowCount = Math.min(8, Math.floor((window.innerWidth * 0.8 - this.scrollBarWidth) / this.emojiButtonSize));
	private updated = false;
	private timeoutId = 0;

	constructor() {
		effect(() => {
			if (this.dialogVisible()) {
				this.nameInput.set(this.authenticationService.name());
			} else if (this.updated) {
				this.requestService.updateProfile();
			}

			this.updated = true;
		});

		effect(() => this.authenticationService.name.set(this.nameInput()));

		this.writeEmojis("");
		this.searchInput.subscribe(search => {
			clearTimeout(this.timeoutId);
			this.timeoutId = setTimeout(() => this.writeEmojis(search), 200);
		});
	}

	selectEmoji(compactEmoji: CompactEmoji) {
		this.authenticationService.avatar.set(compactEmoji.label);
	}

	selectedEmoji(compactEmoji: CompactEmoji) {
		return this.authenticationService.avatar() === compactEmoji.label;
	}

	writeEmojis(search: string) {
		const filteredEmojis = search ? emojisForGroup.map(emojis => emojis.filter(emoji => emojiMatchesTag(emoji.label, search))).filter(emojis => emojis.length > 0) : emojisForGroup;
		const groupedEmojis: CompactEmoji[][] = [];
		filteredEmojis.forEach((emojis, index) => {
			if (index > 0) {
				groupedEmojis.push([]);
			}
			this.writeRow(emojis, groupedEmojis);
		});
		this.emojis.set(groupedEmojis);
	}

	private writeRow<T>(input: T[], output: T[][]) {
		for (let i = 0; i < input.length; i += this.rowCount) {
			const data: T[] = [];
			for (let j = 0; j < this.rowCount; j++) {
				if (i + j < input.length) {
					data.push(input[i + j]);
				}
			}
			output.push(data);
		}
	}
}
