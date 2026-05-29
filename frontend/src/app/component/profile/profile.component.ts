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
		Avatar,
		ButtonModule,
		TooltipModule,
	],
	templateUrl: "./profile.component.html",
	styleUrl: "./profile.component.scss",
})
export class ProfileComponent {
	private readonly authenticationService = inject(AuthenticationService);

	readonly name = input<string | undefined>(undefined);
	readonly avatar = input<string | undefined>(undefined);
	readonly showName = input(false);
	readonly horizontal = input(false);
	readonly large = input(false);
	protected readonly emoji = signal<string | undefined>(undefined);

	constructor() {
		effect(() => {
			const overrideAvatar = this.avatar();
			const profileAvatar = this.authenticationService.avatar();
			this.emoji.set(overrideAvatar !== undefined ? emojiForHexCode[overrideAvatar]?.unicode : (profileAvatar ? emojiForHexCode[profileAvatar]?.unicode : undefined));
		});
	}

	getName() {
		return this.name() ?? this.authenticationService.name();
	}
}
