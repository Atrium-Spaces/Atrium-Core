import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject} from "@angular/core";
import {ActivatedRoute} from "@angular/router";

import {TranslocoDirective} from "@jsverse/transloco";
import {ButtonModule} from "primeng/button";
import {TooltipModule} from "primeng/tooltip";

import {HostChangedEvent, PlayerDisconnectedEvent, PlayerJoinedEvent, PlayerKickedEvent, PlayerLeftEvent, PlayerReconnectedEvent, PlayerUpdatedEvent, RoomEvent, RoomRoomDeletedEvent, RoomSnapshotEvent, SettingsChangedEvent, StateChangedEvent} from "../../model/atrium-event";
import {RequestService} from "../../service/request.service";
import {WebSocketProvider} from "../../utility/websocket-provider";
import {HeaderComponent} from "../header/header.component";

@Component({
	selector: "app-room",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		TooltipModule,
		HeaderComponent,
		TranslocoDirective,
	],
	templateUrl: "./room.component.html",
	styleUrl: "./room.component.scss",
})
export class RoomComponent extends WebSocketProvider<RoomEvent> {
	private readonly changeDetectorRef = inject(ChangeDetectorRef);
	private readonly requestService = inject(RequestService);
	private readonly activatedRoute = inject(ActivatedRoute);

	private readonly roomCode: string;

	constructor() {
		super();
		this.roomCode = this.activatedRoute.snapshot.paramMap.get("code")!;
	}

	protected createWebSocket() {
		return this.requestService.subscribeToRoom(this.roomCode);
	}

	protected onEvent(event: RoomEvent): void {
		console.log(event);
		switch (event.type) {
			case "snapshot":
				this.onSnapshot(event);
				break;
			case "playerJoined":
				this.onPlayerJoined(event);
				break;
			case "playerLeft":
				this.onPlayerLeft(event);
				break;
			case "playerKicked":
				this.onPlayerKicked(event);
				break;
			case "playerUpdated":
				this.onPlayerUpdated(event);
				break;
			case "playerDisconnected":
				this.onPlayerDisconnected(event);
				break;
			case "playerReconnected":
				this.onPlayerReconnected(event);
				break;
			case "hostChanged":
				this.onHostChanged(event);
				break;
			case "settingsChanged":
				this.onSettingsChanged(event);
				break;
			case "stateChanged":
				this.onStateChanged(event);
				break;
			case "roomDeleted":
				this.onRoomDeleted(event);
				break;
		}
		this.changeDetectorRef.markForCheck();
	}

	private onSnapshot(event: RoomSnapshotEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerJoined(event: PlayerJoinedEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerLeft(event: PlayerLeftEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerKicked(event: PlayerKickedEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerUpdated(event: PlayerUpdatedEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerDisconnected(event: PlayerDisconnectedEvent) {
		console.log(event);
		// TODO
	}

	private onPlayerReconnected(event: PlayerReconnectedEvent) {
		console.log(event);
		// TODO
	}

	private onHostChanged(event: HostChangedEvent) {
		console.log(event);
		// TODO
	}

	private onSettingsChanged(event: SettingsChangedEvent) {
		console.log(event);
		// TODO
	}

	private onStateChanged(event: StateChangedEvent) {
		console.log(event);
		// TODO
	}

	private onRoomDeleted(event: RoomRoomDeletedEvent) {
		console.log(event);
		// TODO
	}
}
