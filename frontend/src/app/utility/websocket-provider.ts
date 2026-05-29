import {Directive, OnDestroy, OnInit} from "@angular/core";

const startingReconnectDelay = 1000;
const maxReconnectDelay = 30000;

@Directive()
export abstract class WebSocketProvider<TEvent> implements OnInit, OnDestroy {
	private webSocket?: WebSocket;
	private websocketReconnectTimeoutId = 0;
	private reconnectAttempts = 0;
	private shouldReconnect = true;

	protected abstract createWebSocket(): WebSocket;

	protected abstract onEvent(event: TEvent): void;

	ngOnInit() {
		this.connect();
	}

	ngOnDestroy() {
		this.shouldReconnect = false;
		this.clearReconnectTimeout();
		this.closeWebSocket();
	}

	private connect() {
		this.closeWebSocket();
		this.webSocket = this.createWebSocket();
		this.webSocket.onopen = () => this.reconnectAttempts = 0;
		this.webSocket.onmessage = (event: MessageEvent) => {
			try {
				this.onEvent(JSON.parse(event.data) as TEvent);
			} catch {
				// Ignore malformed frames and keep the socket alive for subsequent events.
			}
		};
		this.webSocket.onerror = () => this.webSocket?.close();
		this.webSocket.onclose = () => {
			this.webSocket = undefined;
			if (this.shouldReconnect) {
				this.scheduleReconnect();
			}
		};
	}

	private scheduleReconnect() {
		this.reconnectAttempts++;
		this.websocketReconnectTimeoutId = setTimeout(() => this.connect(), Math.min(startingReconnectDelay * 2 ** this.reconnectAttempts, maxReconnectDelay));
	}

	private clearReconnectTimeout() {
		if (this.websocketReconnectTimeoutId !== 0) {
			clearTimeout(this.websocketReconnectTimeoutId);
			this.websocketReconnectTimeoutId = 0;
		}
	}

	private closeWebSocket() {
		if (this.webSocket) {
			this.webSocket.onopen = null;
			this.webSocket.onmessage = null;
			this.webSocket.onclose = null;
			this.webSocket.onerror = null;
			this.webSocket.close();
			this.webSocket = undefined;
		}
	}
}
