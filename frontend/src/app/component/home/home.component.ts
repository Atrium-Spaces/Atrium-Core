import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, model} from "@angular/core";
import {FormsModule} from "@angular/forms";
import {Router} from "@angular/router";

import {TranslocoDirective} from "@jsverse/transloco";
import {AvatarModule} from "primeng/avatar";
import {ButtonModule} from "primeng/button";
import {DividerModule} from "primeng/divider";
import {FloatLabelModule} from "primeng/floatlabel";
import {InputTextModule} from "primeng/inputtext";
import {TooltipModule} from "primeng/tooltip";

import {HomeEvent, HomeRoomCreatedEvent, HomeRoomDeletedEvent, HomeRoomUpdatedEvent, HomeSnapshotEvent} from "../../model/atrium-event";
import {RequestService} from "../../service/request.service";

import {WebSocketProvider} from "../../utility/websocket-provider";
import {EditProfileComponent} from "../edit-profile/edit-profile.component";
import {HeaderComponent} from "../header/header.component";
import {ProfileComponent} from "../profile/profile.component";

@Component({
	selector: "app-home",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		AvatarModule,
		FloatLabelModule,
		InputTextModule,
		ButtonModule,
		TooltipModule,
		DividerModule,
		TranslocoDirective,
		HeaderComponent,
		FormsModule,
		EditProfileComponent,
		ProfileComponent,
	],
	templateUrl: "./home.component.html",
	styleUrl: "./home.component.scss",
})
export class HomeComponent extends WebSocketProvider<HomeEvent> {
	private readonly changeDetectorRef = inject(ChangeDetectorRef);
	private readonly router = inject(Router);
	private readonly requestService = inject(RequestService);

	protected dialogVisible = model(false);
	protected readonly loading = model(false);
	protected readonly roomCodeLength = 6;

	updateRoomCode(roomCodeInput: HTMLInputElement) {
		const value = roomCodeInput.value;
		const newValue = value.toUpperCase().replaceAll(/[^A-Z\d]/g, "");
		if (newValue !== value) {
			roomCodeInput.value = newValue;
		}
	}

	joinRoom(event: SubmitEvent, roomCode: string) {
		event.stopPropagation();
		this.router.navigate(["/room", roomCode]).then();
	}

	createRoom() {
		this.requestService.createRoom(1, 1, true).subscribe({
			next: room => {
				this.loading.set(false);
				this.router.navigate(["/room", room.code]).then();
			},
			error: () => this.loading.set(false),
		});
	}

	protected createWebSocket() {
		return this.requestService.subscribeToHome();
	}

	protected onEvent(event: HomeEvent): void {
		switch (event.type) {
			case "snapshot":
				this.onSnapshot(event);
				break;
			case "roomCreated":
				this.onRoomCreated(event);
				break;
			case "roomUpdated":
				this.onRoomUpdated(event);
				break;
			case "roomDeleted":
				this.onRoomDeleted(event);
				break;
		}
		this.changeDetectorRef.markForCheck();
	}

	private onSnapshot(event: HomeSnapshotEvent) {
		console.log(event);
		// TODO
	}

	private onRoomCreated(event: HomeRoomCreatedEvent) {
		console.log(event);
		// TODO
	}

	private onRoomUpdated(event: HomeRoomUpdatedEvent) {
		console.log(event);
		// TODO
	}

	private onRoomDeleted(event: HomeRoomDeletedEvent) {
		console.log(event);
		// TODO
	}
}
