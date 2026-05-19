package org.atrium.core.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import org.atrium.core.api.dto.AuthenticatedRequest;
import org.atrium.core.api.dto.CreateRoomRequest;
import org.atrium.core.api.dto.JoinRoomRequest;
import org.atrium.core.api.dto.KickPlayerRequest;
import org.atrium.core.api.dto.RoomListResponse;
import org.atrium.core.api.dto.RoomView;
import org.atrium.core.api.dto.StatusRequest;
import org.atrium.core.api.dto.StatusResponse;
import org.atrium.core.api.dto.UpdateProfileRequest;
import org.atrium.core.api.dto.UpdateRoomSettingsRequest;
import org.atrium.core.domain.service.PlayerService;
import org.atrium.core.domain.service.RoomService;
import org.atrium.core.domain.service.RoomViewAssembler;

/**
 * Reactive REST surface for the lobby system.
 *
 * <p>All write endpoints take an {@code AuthenticatedRequest}-shaped body (the player's
 * {@code publicId} + {@code secretId} pair) so the secret id never ends up in URLs /
 * access logs. The matching {@link org.atrium.core.domain.service.PlayerService#authenticate}
 * call gates every operation.
 */
@RestController
@RequestMapping("/api/lobby")
@RequiredArgsConstructor
public class LobbyController {

	private final RoomService roomService;
	private final PlayerService playerService;
	private final RoomViewAssembler viewAssembler;

	// ---- status ------------------------------------------------------------------------------

	@PostMapping("/status")
	public Mono<StatusResponse> status(@RequestBody StatusRequest request) {
		return playerService
			.ensureIdentity(request.publicId(), request.secretId(), request.name(), request.avatar())
			.flatMap(result -> {
				Mono<RoomView> activeRoom = playerService.resolveRoom(result.player().publicId())
					.flatMap(viewAssembler::assemble);
				return activeRoom
					.map(view -> buildStatus(result, view))
					.defaultIfEmpty(buildStatus(result, null));
			});
	}

	private StatusResponse buildStatus(PlayerService.IdentityResult result, @org.jspecify.annotations.Nullable RoomView activeRoom) {
		return new StatusResponse(
			result.player().publicId(),
			result.player().secretId(),
			result.player().name(),
			result.player().avatar(),
			result.freshIdentity(),
			activeRoom);
	}

	// ---- profile -----------------------------------------------------------------------------

	@PostMapping("/profile")
	public Mono<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return playerService
			.updateProfile(request.publicId(), request.secretId(), request.name(), request.avatar())
			.flatMap(roomService::broadcastProfileUpdate);
	}

	// ---- rooms -------------------------------------------------------------------------------

	@GetMapping("/rooms")
	public Mono<RoomListResponse> listPublicRooms(@RequestParam(defaultValue = "50") int limit) {
		return roomService.listPublic(Math.max(1, Math.min(limit, 200)))
			.collectList()
			.map(RoomListResponse::new);
	}

	@GetMapping("/rooms/{code}")
	public Mono<RoomView> getRoom(@PathVariable String code) {
		return roomService.view(code);
	}

	@PostMapping("/rooms")
	public Mono<ResponseEntity<RoomView>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
		return roomService.createRoom(
				request.publicId(),
				request.secretId(),
				request.maxPlayers(),
				request.gameSettings(),
				request.isPrivate())
			.map(view -> ResponseEntity.status(201).body(view));
	}

	@PostMapping("/rooms/{code}/join")
	public Mono<RoomView> joinRoom(@PathVariable String code, @Valid @RequestBody JoinRoomRequest request) {
		return roomService.joinRoom(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/leave")
	public Mono<Void> leaveRoom(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		return roomService.leaveRoom(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/kick")
	public Mono<Void> kickPlayer(@PathVariable String code, @Valid @RequestBody KickPlayerRequest request) {
		return roomService.kickPlayer(code, request.publicId(), request.secretId(), request.targetPublicId());
	}

	@DeleteMapping("/rooms/{code}")
	public Mono<Void> deleteRoom(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		return roomService.deleteRoom(code, request.publicId(), request.secretId());
	}

	@PatchMapping("/rooms/{code}/settings")
	public Mono<RoomView> updateSettings(
		@PathVariable String code,
		@Valid @RequestBody UpdateRoomSettingsRequest request) {
		return roomService.updateSettings(
			code,
			request.publicId(),
			request.secretId(),
			request.maxPlayers(),
			request.gameSettings(),
			request.isPrivate());
	}

	@PostMapping("/rooms/{code}/start")
	public Mono<RoomView> startGame(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		return roomService.startGame(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/stop")
	public Mono<RoomView> stopGame(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		return roomService.stopGame(code, request.publicId(), request.secretId());
	}
}

