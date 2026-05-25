export interface PlayerView {
	publicId: string;
	name: string;
	avatar: string;
	status: "ACTIVE" | "DISCONNECTED";
	joinedAt: string;
}

export interface RoomView {
	code: string;
	name: string | null;
	host: string;
	players: PlayerView[];
	minPlayers: number;
	maxPlayers: number;
	gameSettings: Record<string, unknown>;
	isPrivate: boolean;
	state: "LOBBY" | "IN_GAME";
	createdAt: string;
	lastActivityAt: string;
}

export interface HomeSnapshotEvent {
	type: "snapshot";
	rooms: RoomView[];
}

export interface HomeRoomCreatedEvent {
	type: "roomCreated";
	room: RoomView;
}

export interface HomeRoomUpdatedEvent {
	type: "roomUpdated";
	room: RoomView;
}

export interface HomeRoomDeletedEvent {
	type: "roomDeleted";
	roomCode: string;
}

export type HomeEvent = HomeSnapshotEvent | HomeRoomCreatedEvent | HomeRoomUpdatedEvent | HomeRoomDeletedEvent;

export interface RoomSnapshotEvent {
	type: "snapshot";
	room: RoomView;
	roomCode: string;
	emittedAt: string;
}

export interface PlayerJoinedEvent {
	type: "playerJoined";
	player: PlayerView;
	roomCode: string;
	emittedAt: string;
}

export interface PlayerLeftEvent {
	type: "playerLeft";
	publicId: string;
	reason?: "left" | "kicked";
	roomCode: string;
	emittedAt: string;
}

export interface PlayerKickedEvent {
	type: "playerKicked";
	publicId: string;
	roomCode: string;
	emittedAt: string;
}

export interface PlayerUpdatedEvent {
	type: "playerUpdated";
	player: PlayerView;
	roomCode: string;
	emittedAt: string;
}

export interface PlayerDisconnectedEvent {
	type: "playerDisconnected";
	publicId: string;
	roomCode: string;
	emittedAt: string;
}

export interface PlayerReconnectedEvent {
	type: "playerReconnected";
	publicId: string;
	roomCode: string;
	emittedAt: string;
}

export interface HostChangedEvent {
	type: "hostChanged";
	newHost: string;
	roomCode: string;
	emittedAt: string;
}

export interface SettingsChangedEvent {
	type: "settingsChanged";
	room: RoomView;
	roomCode: string;
	emittedAt: string;
}

export interface StateChangedEvent {
	type: "stateChanged";
	newState: "LOBBY" | "IN_GAME";
	roomCode: string;
	emittedAt: string;
}

export interface RoomRoomDeletedEvent {
	type: "roomDeleted";
	roomCode: string;
	emittedAt: string;
}

export type RoomEvent =
	| RoomSnapshotEvent
	| PlayerJoinedEvent
	| PlayerLeftEvent
	| PlayerKickedEvent
	| PlayerUpdatedEvent
	| PlayerDisconnectedEvent
	| PlayerReconnectedEvent
	| HostChangedEvent
	| SettingsChangedEvent
	| StateChangedEvent
	| RoomRoomDeletedEvent;
