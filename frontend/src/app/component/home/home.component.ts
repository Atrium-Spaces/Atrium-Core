import {ChangeDetectionStrategy, ChangeDetectorRef, Component, inject, model, signal} from "@angular/core";
import {FormsModule} from "@angular/forms";
import {Router} from "@angular/router";

import {TranslocoDirective} from "@jsverse/transloco";
import {AvatarModule} from "primeng/avatar";
import {ButtonModule} from "primeng/button";
import {DividerModule} from "primeng/divider";
import {FloatLabelModule} from "primeng/floatlabel";
import {InputTextModule} from "primeng/inputtext";
import {PanelModule} from "primeng/panel";
import {TooltipModule} from "primeng/tooltip";

import {HomeEvent, HomeRoomCreatedEvent, HomeRoomDeletedEvent, HomeRoomUpdatedEvent, HomeSnapshotEvent, RoomView} from "../../model/atrium-event";
import {RequestService} from "../../service/request.service";
import {cleanRoomCode} from "../../utility/utilities";
import {WebSocketProvider} from "../../utility/websocket-provider";
import {ButtonWithLoadingComponent} from "../button-with-loading/button-with-loading.component";
import {EditProfileComponent} from "../edit-profile/edit-profile.component";
import {HeaderComponent} from "../header/header.component";
import {ProfileComponent} from "../profile/profile.component";

@Component({
	selector: "app-home",
	changeDetection: ChangeDetectionStrategy.OnPush,
	imports: [
		AvatarModule,
		ButtonModule,
		ButtonWithLoadingComponent,
		DividerModule,
		EditProfileComponent,
		FloatLabelModule,
		FormsModule,
		HeaderComponent,
		InputTextModule,
		PanelModule,
		ProfileComponent,
		TooltipModule,
		TranslocoDirective,
	],
	templateUrl: "./home.component.html",
	styleUrl: "./home.component.scss",
})
export class HomeComponent extends WebSocketProvider<HomeEvent> {
	private readonly changeDetectorRef = inject(ChangeDetectorRef);
	private readonly router = inject(Router);
	private readonly requestService = inject(RequestService);

	protected dialogVisible = model(false);
	protected readonly roomCodeLength = 6;
	protected readonly publicRooms = signal<RoomView[]>([]);

	updateRoomCode(roomCodeInput: HTMLInputElement) {
		const value = roomCodeInput.value;
		const newValue = cleanRoomCode(value);
		if (newValue !== value) {
			roomCodeInput.value = newValue;
		}
	}


	createRoom() {
		return this.requestService.createRoom(true);
	}

	navigateToRoom(roomCode: string) {
		this.router.navigate(["/room", roomCode]).then();
	}

	getHostName(room: RoomView) {
		return room.players.find(player => player.publicId === room.host)?.name ?? room.host;
	}

	protected createWebSocket() {
		return this.requestService.subscribeToHome();
	}

	protected onEvent(event: HomeEvent) {
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
		this.publicRooms.set(this.sortByLastActivity(event.rooms));
	}

	private onRoomCreated(event: HomeRoomCreatedEvent) {
		this.upsertRoom(event.room);
	}

	private onRoomUpdated(event: HomeRoomUpdatedEvent) {
		this.upsertRoom(event.room);
	}

	private onRoomDeleted(event: HomeRoomDeletedEvent) {
		this.publicRooms.set(this.publicRooms().filter(room => room.code !== event.roomCode));
	}

	private upsertRoom(updatedRoom: RoomView) {
		const roomsWithoutUpdated = this.publicRooms().filter(room => room.code !== updatedRoom.code);
		this.publicRooms.set(this.sortByLastActivity([updatedRoom, ...roomsWithoutUpdated]));
	}

	private sortByLastActivity(rooms: RoomView[]) {
		return [...rooms].sort((leftRoom, rightRoom) => Date.parse(rightRoom.lastActivityAt) - Date.parse(leftRoom.lastActivityAt));
	}
}
