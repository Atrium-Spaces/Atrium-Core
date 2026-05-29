import {ChangeDetectionStrategy, Component, DestroyRef, inject, signal} from "@angular/core";
import {takeUntilDestroyed} from "@angular/core/rxjs-interop";
import {NavigationStart, Router, RouterOutlet} from "@angular/router";

import {AuthenticationService} from "./service/authentication.service";
import {RequestService} from "./service/request.service";
import {getRoomCodeFromUrl} from "./utility/utilities";

@Component({
	selector: "app-root",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		RouterOutlet,
	],
	templateUrl: "./app.component.html",
	styleUrl: "./app.component.scss",
})
export class AppComponent {
	private readonly destroyRef = inject(DestroyRef);
	private readonly router = inject(Router);
	private readonly requestService = inject(RequestService);
	private readonly authenticationService = inject(AuthenticationService);

	protected readonly status = signal<"loading" | "success" | "error">("loading");

	constructor() {
		this.init();
	}

	init() {
		this.status.set("loading");
		this.requestService.init().subscribe({
			next: ({publicId, secretId, name, avatar}) => {
				this.authenticationService.publicId.set(publicId);
				this.authenticationService.secretId.set(secretId);
				this.authenticationService.name.set(name);
				this.authenticationService.avatar.set(avatar);
				this.status.set("success");
			},
			error: () => this.status.set("error"),
		});

		this.router.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(event => {
			if (event instanceof NavigationStart) {
				const roomCode = getRoomCodeFromUrl(event.url);
				if (roomCode) {
					this.status.set("loading");
					this.requestService.getRoom(roomCode).subscribe({
						next: () => this.status.set("success"),
						error: () => {
							this.router.navigate(["/"]).then();
							this.status.set("success");
						},
					});
				}
			}
		});
	}
}
