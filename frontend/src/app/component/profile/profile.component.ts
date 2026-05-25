import {ChangeDetectionStrategy, Component, effect, inject, input, signal} from "@angular/core";

import {Avatar} from "primeng/avatar";
import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";

import {AuthenticationService} from "../../service/authentication.service";
import {emojiForHexCode} from "../../utility/emoji";

@Component({
	selector: "app-profile",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		TooltipModule,
		Avatar,
	],
	templateUrl: "./profile.component.html",
	styleUrl: "./profile.component.scss",
})
export class ProfileComponent {
	private readonly authenticationService = inject(AuthenticationService);

	readonly showName = input(false);
	protected readonly emoji = signal<string | undefined>(undefined);

	constructor() {
		effect(() => {
			const avatar = this.authenticationService.avatar();
			this.emoji.set(avatar ? emojiForHexCode[avatar]?.unicode : undefined);
		});
	}

	getName() {
		return this.authenticationService.name();
	}
}
