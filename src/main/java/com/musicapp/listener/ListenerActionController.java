package com.musicapp.listener;

import com.musicapp.listener.dto.AttitudeRequest;
import com.musicapp.listener.dto.ListeningHistoryResponse;
import com.musicapp.listener.dto.PerformerActionResponse;
import com.musicapp.listener.dto.SongActionResponse;
import com.musicapp.catalog.dto.SongResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/listener/me")
@RequiredArgsConstructor
public class ListenerActionController {
    private final ListenerActionService listenerActionService;

    @GetMapping("/library")
    public List<SongResponse> getFavoriteSongs (Authentication authentication) {
        return listenerActionService.getFavoriteSongs(authentication.getName());
    }

    @GetMapping("/songs/{songId}")
    public SongActionResponse getSongState (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.getSongState(authentication.getName(), songId);
    }

    @PostMapping("/songs/{songId}/stream")
    public SongActionResponse streamSong (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.streamSong(authentication.getName(), songId, false);
    }

    @PostMapping("/songs/{songId}/skip")
    public SongActionResponse skipSong (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.streamSong(authentication.getName(), songId, true);
    }

    @PutMapping("/songs/{songId}/attitude")
    public SongActionResponse setSongAttitude (Authentication authentication, @PathVariable Long songId, @Valid @RequestBody AttitudeRequest request) {
        return listenerActionService.setSongAttitude(authentication.getName(), songId, request.attitude());
    }

    @DeleteMapping("/songs/{songId}/attitude")
    public SongActionResponse clearSongAttitude (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.clearSongAttitude(authentication.getName(), songId);
    }

    @PutMapping("/songs/{songId}/block")
    public SongActionResponse blockSong (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.blockSong(authentication.getName(), songId);
    }

    @DeleteMapping("/songs/{songId}/block")
    public SongActionResponse unblockSong (Authentication authentication, @PathVariable Long songId) {
        return listenerActionService.unblockSong(authentication.getName(), songId);
    }

    @GetMapping("/history")
    public Page<ListeningHistoryResponse> getListeningHistory(Pageable pageable, Authentication authentication, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to, @RequestParam(required = false) Boolean skipped) {
        return listenerActionService.getListeningHistory(pageable, authentication.getName(), from, to, skipped);
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> deleteListeningHistory (Authentication authentication) {
        listenerActionService.deleteListeningHistory(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/performers/{performerId}")
    public PerformerActionResponse getPerformerState (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.getPerformerState(authentication.getName(), performerId);
    }

    @PutMapping("/performers/{performerId}/follow")
    public PerformerActionResponse followPerformer (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.followPerformer(authentication.getName(), performerId);
    }

    @DeleteMapping("/performers/{performerId}/follow")
    public PerformerActionResponse unfollowPerformer (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.unfollowPerformer(authentication.getName(), performerId);
    }

    @PutMapping("/performers/{performerId}/attitude")
    public PerformerActionResponse setPerformerAttitude (Authentication authentication, @PathVariable Long performerId, @Valid @RequestBody AttitudeRequest request) {
        return listenerActionService.setPerformerAttitude(authentication.getName(), performerId, request.attitude());
    }

    @DeleteMapping("/performers/{performerId}/attitude")
    public PerformerActionResponse clearPerformerAttitude (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.clearPerformerAttitude(authentication.getName(), performerId);
    }

    @PutMapping("/performers/{performerId}/block")
    public PerformerActionResponse blockPerformer (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.blockPerformer(authentication.getName(), performerId);
    }

    @DeleteMapping("/performers/{performerId}/block")
    public PerformerActionResponse unblockPerformer (Authentication authentication, @PathVariable Long performerId) {
        return listenerActionService.unblockPerformer(authentication.getName(), performerId);
    }
}
