import {ChangeDetectionStrategy, ChangeDetectorRef, Component, effect, inject, model, signal} from "@angular/core";
import {FormsModule} from "@angular/forms";
import {Router} from "@angular/router";

import {TranslocoDirective} from "@jsverse/transloco";
import {ButtonModule} from "primeng/button";
import {DividerModule} from "primeng/divider";
import {OverlayBadgeModule} from "primeng/overlaybadge";
import {PanelModule} from "primeng/panel";
import {SliderModule} from "primeng/slider";
import {ToggleSwitchModule} from "primeng/toggleswitch";
import {TooltipModule} from "primeng/tooltip";

import {HostChangedEvent, PlayerDisconnectedEvent, PlayerJoinedEvent, PlayerKickedEvent, PlayerLeftEvent, PlayerReconnectedEvent, PlayerUpdatedEvent, PlayerView, RoomEvent, RoomSnapshotEvent, RoomView, SettingsChangedEvent, StateChangedEvent} from "../../model/atrium-event";
import {AuthenticationService} from "../../service/authentication.service";
import {RequestService} from "../../service/request.service";
import {getRoomCodeFromUrl} from "../../utility/utilities";
import {WebSocketProvider} from "../../utility/websocket-provider";
import {ButtonWithLoadingComponent} from "../button-with-loading/button-with-loading.component";
import {EditSettingsComponent} from "../edit-settings/edit-settings.component";
import {HeaderComponent} from "../header/header.component";
import {ProfileComponent} from "../profile/profile.component";

@Component({
	selector: "app-room",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		ButtonModule,
		ButtonWithLoadingComponent,
		DividerModule,
		EditSettingsComponent,
		FormsModule,
		HeaderComponent,
		OverlayBadgeModule,
		PanelModule,
		ProfileComponent,
		SliderModule,
		ToggleSwitchModule,
		TooltipModule,
		TranslocoDirective,
	],
	templateUrl: "./room.component.html",
	styleUrl: "./room.component.scss",
})
export class RoomComponent extends WebSocketProvider<RoomEvent> {
	private readonly changeDetectorRef = inject(ChangeDetectorRef);
	private readonly router = inject(Router);
	private readonly authenticationService = inject(AuthenticationService);
	private readonly requestService = inject(RequestService);

	protected readonly roomCode: string;

	protected readonly room;
	protected joinedRoom = signal(false);
	protected dialogVisible = model(false);

	constructor() {
		super();
		this.roomCode = getRoomCodeFromUrl(this.router.url)!;
		this.room = signal<RoomView>({
			code: this.roomCode,
			name: null,
			host: "",
			players: [],
			minPlayers: 1,
			maxPlayers: 1,
			absoluteMinPlayers: 1,
			absoluteMaxPlayers: 1,
			gameSettings: {},
			isPrivate: true,
			state: "LOBBY",
			createdAt: "",
			lastActivityAt: "",
		});

		effect(() => {
			const isMember = this.room().players.some(player => player.publicId === this.authenticationService.publicId());
			this.joinedRoom.set(isMember);
		});
	}

	protected createWebSocket() {
		return this.requestService.subscribeToRoom(this.roomCode);
	}

	protected onEvent(event: RoomEvent) {
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
				this.navigateToHome();
				break;
		}
		this.changeDetectorRef.markForCheck();
	}

	getHostName() {
		return this.room().players.find(player => player.publicId === this.room().host)?.name;
	}

	isHost() {
		return this.authenticationService.publicId() === this.room().host;
	}

	onJoinRoomChanged(joinedRoom: boolean) {
		if (joinedRoom === this.joinedRoom()) {
			return;
		}

		this.joinedRoom.set(joinedRoom);
		if (joinedRoom) {
			this.requestService.joinRoom(this.roomCode).subscribe({error: () => this.joinedRoom.set(false)});
		} else {
			this.requestService.leaveRoom(this.roomCode).subscribe({error: () => this.joinedRoom.set(true)});
		}
	}

	startGame() {
		return this.requestService.startGame(this.roomCode);
	}

	stopGame() {
		return this.requestService.stopGame(this.roomCode);
	}

	deleteRoom() {
		return this.requestService.deleteRoom(this.roomCode);
	}

	navigateToHome() {
		this.router.navigate(["/"]).then();
	}

	kickPlayer(playerId: string) {
		this.requestService.kickPlayer(this.roomCode, playerId).subscribe();
	}

	private updatePlayers(players: PlayerView[]) {
		this.room.set({...this.room(), players});
	}

	private onSnapshot(event: RoomSnapshotEvent) {
		this.room.set(event.room);
	}

	private onPlayerJoined(event: PlayerJoinedEvent) {
		this.updatePlayers([...this.room().players, event.player]);
	}

	private onPlayerLeft(event: PlayerLeftEvent) {
		this.updatePlayers(this.room().players.filter(player => player.publicId !== event.publicId));
	}

	private onPlayerKicked(event: PlayerKickedEvent) {
		this.updatePlayers(this.room().players.filter(player => player.publicId !== event.publicId));
	}

	private onPlayerUpdated(event: PlayerUpdatedEvent) {
		this.updatePlayers(this.room().players.map(player => player.publicId === event.player.publicId ? event.player : player));
	}

	private onPlayerDisconnected(event: PlayerDisconnectedEvent) {
		this.updatePlayers(this.room().players.map(player => player.publicId === event.publicId ? {...player, status: "DISCONNECTED"} : player));
	}

	private onPlayerReconnected(event: PlayerReconnectedEvent) {
		this.updatePlayers(this.room().players.map(player => player.publicId === event.publicId ? {...player, status: "ACTIVE"} : player));
	}

	private onHostChanged(event: HostChangedEvent) {
		this.room.set({...this.room(), host: event.newHost});
	}

	private onSettingsChanged(event: SettingsChangedEvent) {
		this.room.set(event.room);
	}

	private onStateChanged(event: StateChangedEvent) {
		this.room.set({...this.room(), state: event.newState});
	}
}
