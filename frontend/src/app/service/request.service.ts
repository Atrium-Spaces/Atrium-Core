import {inject, Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";

import {Observable} from "rxjs";

import {PlayerView, RoomView} from "../model/atrium-event";
import {AuthenticationService} from "./authentication.service";

const baseUrl = "/api/atrium";

@Injectable({providedIn: "root"})
export class RequestService {
	private readonly httpClient = inject(HttpClient);
	private readonly authenticationService = inject(AuthenticationService);

	init() {
		return this.httpClient.post<StatusResponse>(`${baseUrl}/status`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	updateProfile() {
		return this.httpClient.post<PlayerView>(`${baseUrl}/profile`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			name: this.authenticationService.name(),
			avatar: this.authenticationService.avatar(),
		});
	}

	createRoom(isPrivate: boolean) {
		return this.httpClient.post<RoomView>(`${baseUrl}/rooms`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			isPrivate,
		});
	}

	getRoom(code: string) {
		return this.httpClient.get<RoomView>(`${baseUrl}/rooms/${code}`);
	}

	joinRoom(code: string) {
		return this.httpClient.post<RoomView>(`${baseUrl}/rooms/${code}/join`, {
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

	updateSettings(code: string, name: string, minPlayers: number, maxPlayers: number, isPrivate: boolean) {
		return this.httpClient.patch<RoomView>(`${baseUrl}/rooms/${code}/settings`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
			name,
			minPlayers,
			maxPlayers,
			isPrivate,
		});
	}

	startGame(code: string): Observable<RoomView> {
		return this.httpClient.post<RoomView>(`${baseUrl}/rooms/${code}/start`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	stopGame(code: string): Observable<RoomView> {
		return this.httpClient.post<RoomView>(`${baseUrl}/rooms/${code}/stop`, {
			publicId: this.authenticationService.publicId(),
			secretId: this.authenticationService.secretId(),
		});
	}

	subscribeToHome() {
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
	activeRooms: RoomView[];
}
