import {inject, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";

import {Observable} from "rxjs";

import {AuthenticationService} from "./authentication.service";

const baseUrl = "/api/atrium";

@Injectable({providedIn: "root"})
export class RequestService {
	private readonly httpClient = inject(HttpClient);
	private readonly authenticationService = inject(AuthenticationService);

	init() {
		this.httpClient.post<StatusResponse>(`${baseUrl}/status`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		}).subscribe({
			next: ({publicId, secretId, name, avatar}) => {
				this.authenticationService.publicId.set(publicId);
				this.authenticationService.secretId.set(secretId);
				this.authenticationService.name.set(name);
				this.authenticationService.avatar.set(avatar);
			},
			error: error => console.error("Status check failed", error),
		});
	}

	updateProfile() {
		this.httpClient.post<void>(`${baseUrl}/profile`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			name: this.authenticationService.name(),
			avatar: this.authenticationService.avatar(),
		}).subscribe();
	}

	createRoom(minPlayers: number, maxPlayers: number, isPrivate: boolean) {
		return this.httpClient.post<RoomDTO>(`${baseUrl}/rooms`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			minPlayers,
			maxPlayers,
			isPrivate,
		});
	}

	listPublicRooms() {
		return this.httpClient.get<RoomDTO[]>(`${baseUrl}/rooms`);
	}

	getRoom(code: string) {
		return this.httpClient.get<RoomDTO>(`${baseUrl}/rooms/${code}`);
	}

	joinRoom(code: string) {
		return this.httpClient.post<RoomDTO>(`${baseUrl}/rooms/${code}/join`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	leaveRoom(code: string) {
		return this.httpClient.post<void>(`${baseUrl}/rooms/${code}/leave`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	kickPlayer(code: string, targetPublicId: string) {
		return this.httpClient.post<void>(`${baseUrl}/rooms/${code}/kick`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			targetPublicId,
		});
	}

	deleteRoom(code: string) {
		return this.httpClient.delete<void>(`${baseUrl}/rooms/${code}`, {
			body: {
				publicId: this.authenticationService.publicId(),
				secretId: this.authenticationService.secretId(),
			},
		});
	}

	updateSettings(code: string, minPlayers: number | undefined, maxPlayers: number | undefined, isPrivate: boolean | undefined) {
		return this.httpClient.patch<RoomDTO>(`${baseUrl}/rooms/${code}/settings`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			minPlayers,
			maxPlayers,
			isPrivate,
		});
	}

	startGame(code: string): Observable<RoomDTO> {
		return this.httpClient.post<RoomDTO>(`${baseUrl}/rooms/${code}/start`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	stopGame(code: string): Observable<RoomDTO> {
		return this.httpClient.post<RoomDTO>(`${baseUrl}/rooms/${code}/stop`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	subscribeToHome(): WebSocket {
		return new WebSocket(`${RequestService.getWebsocketBaseUrl()}/home`);
	}

	subscribeToRoom(code: string) {
		return new WebSocket(`${RequestService.getWebsocketBaseUrl()}/${code}?publicId=${this.authenticationService.publicId()}&secretId=${this.authenticationService.secretId()}`);
	}

	private static getWebsocketBaseUrl() {
		const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
		const host = window.location.host;
		return `${protocol}//${host}/api/atrium/ws`;
	}
}

interface StatusResponse {
	publicId: string;
	secretId: string;
	name: string;
	avatar: string;
	freshIdentity: boolean;
	activeRoom?: RoomDTO;
}

interface PlayerDTO {
	publicId: string;
	name: string;
	avatar: string;
	status: "ACTIVE" | "DISCONNECTED";
	joinedAt: string;
}

interface RoomDTO {
	code: string;
	name: string | null;
	host: string;
	players: PlayerDTO[];
	minPlayers: number;
	maxPlayers: number;
	gameSettings: Record<string, unknown>;
	isPrivate: boolean;
	state: "LOBBY" | "IN_GAME";
	createdAt: string;
	lastActivityAt: string;
}
