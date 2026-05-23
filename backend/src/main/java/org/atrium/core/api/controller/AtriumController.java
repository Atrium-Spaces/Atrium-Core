package org.atrium.core.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.atrium.core.api.dto.*;
import org.atrium.core.domain.service.PlayerService;
import org.atrium.core.domain.service.RoomService;
import org.atrium.core.domain.service.RoomViewAssembler;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive REST surface for the lobby system.
 *
 * <p>All write endpoints take an {@code AuthenticatedRequest}-shaped body (the player's
 * {@code publicId} + {@code secretId} pair) so the secret id never ends up in URLs /
 * access logs. The matching {@link PlayerService#authenticate}
 * call gates every operation.
 */
@RestController
@RequestMapping("/api/atrium")
@RequiredArgsConstructor
@Slf4j
public final class AtriumController {

	private final RoomService roomService;
	private final PlayerService playerService;
	private final RoomViewAssembler roomViewAssembler;

	@PostMapping("/status")
	public Mono<StatusResponse> status(@RequestBody AuthenticatedRequest request) {
		log.debug("Status check requested for publicId={}", request.publicId());
		return playerService.ensureIdentity(request.publicId(), request.secretId())
			.flatMap(result -> playerService.resolveRoom(result.player().publicId())
				.flatMap(roomViewAssembler::assemble)
				.map(roomView -> buildStatus(result, roomView))
				.defaultIfEmpty(buildStatus(result, null)));
	}

	@PostMapping("/profile")
	public Mono<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		log.debug("Profile update requested for player {}", request.publicId());
		return playerService.updateProfile(request.publicId(), request.secretId(), request.name(), request.avatar())
			.flatMap(roomService::broadcastProfileUpdate);
	}

	@GetMapping("/rooms")
	public Flux<RoomView> listPublicRooms(@RequestParam(defaultValue = "50") int limit) {
		log.debug("Public room list requested with limit={}", limit);
		return roomService.listPublic(Math.clamp(limit, 1, 200));
	}

	@GetMapping("/rooms/{code}")
	public Mono<RoomView> getRoom(@PathVariable String code) {
		log.debug("Room view requested for code={}", code);
		return roomService.view(code);
	}

	@PostMapping("/rooms")
	public Mono<ResponseEntity<RoomView>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
		log.debug("Create room requested by player {}", request.publicId());
		return roomService.createRoom(request.publicId(), request.secretId(), request.name(), request.minPlayers(), request.maxPlayers(), request.gameSettings(), request.isPrivate())
			.map(roomView -> ResponseEntity.status(201).body(roomView));
	}

	@PostMapping("/rooms/{code}/join")
	public Mono<RoomView> joinRoom(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		log.debug("Join room requested: player={} room={}", request.publicId(), code);
		return roomService.joinRoom(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/leave")
	public Mono<Void> leaveRoom(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		log.debug("Leave room requested: player={} room={}", request.publicId(), code);
		return roomService.leaveRoom(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/kick")
	public Mono<Void> kickPlayer(@PathVariable String code, @Valid @RequestBody KickPlayerRequest request) {
		log.debug("Kick requested by host {} in room {} for target {}", request.publicId(), code, request.targetPublicId());
		return roomService.kickPlayer(code, request.publicId(), request.secretId(), request.targetPublicId());
	}

	@DeleteMapping("/rooms/{code}")
	public Mono<Void> deleteRoom(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		log.debug("Delete room requested: player={} room={}", request.publicId(), code);
		return roomService.deleteRoom(code, request.publicId(), request.secretId());
	}

	@PatchMapping("/rooms/{code}/settings")
	public Mono<RoomView> updateSettings(@PathVariable String code, @Valid @RequestBody UpdateRoomSettingsRequest request) {
		log.debug("Update settings requested by host {} for room {}", request.publicId(), code);
		return roomService.updateSettings(code, request.publicId(), request.secretId(), request.name(), request.minPlayers(), request.maxPlayers(), request.gameSettings(), request.isPrivate());
	}

	@PostMapping("/rooms/{code}/start")
	public Mono<RoomView> startGame(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		log.debug("Start game requested by host {} for room {}", request.publicId(), code);
		return roomService.startGame(code, request.publicId(), request.secretId());
	}

	@PostMapping("/rooms/{code}/stop")
	public Mono<RoomView> stopGame(@PathVariable String code, @Valid @RequestBody AuthenticatedRequest request) {
		log.debug("Stop game requested by host {} for room {}", request.publicId(), code);
		return roomService.stopGame(code, request.publicId(), request.secretId());
	}

	private static StatusResponse buildStatus(PlayerService.IdentityResult result, @Nullable RoomView activeRoom) {
		return new StatusResponse(result.player().publicId(), result.player().secretId(), result.player().name(), result.player().avatar(), result.freshIdentity(), activeRoom);
	}
}
