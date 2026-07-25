package com.musicapp.playlist;

import com.musicapp.playlist.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController {
    private final PlaylistService playlistService;

    @GetMapping
    public List<PlaylistSummaryResponse> findPlaylists (@RequestParam(required = false) String search, @RequestParam(required = false) String type, @RequestParam(required = false) Long creatorId, @RequestParam(required = false) Long memberId) {
        return playlistService.findPlaylists(search, parsePlaylistType(type), creatorId, memberId);
    }

    @GetMapping("/{playlistId}")
    public PlaylistResponse getPlaylist (@PathVariable Long playlistId) {
        return playlistService.getPlaylist(playlistId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistResponse createPlaylist (Authentication authentication, @Valid @RequestBody CreatePlaylistRequest request) {
        return playlistService.createPlaylist(authentication.getName(), request);
    }

    @PutMapping("/{playlistId}")
    public PlaylistResponse updatePlaylist (Authentication authentication, @PathVariable Long playlistId, @Valid @RequestBody UpdatePlaylistRequest request) {
        return playlistService.updatePlaylist(authentication.getName(), playlistId, request);
    }

    @DeleteMapping("/{playlistId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist (Authentication authentication, @PathVariable Long playlistId) {
        playlistService.deletePlaylist(authentication.getName(), playlistId);
    }

    @PutMapping("/{playlistId}/members/me")
    public PlaylistResponse joinPlaylist (Authentication authentication, @PathVariable Long playlistId) {
        return playlistService.joinPlaylist(authentication.getName(), playlistId);
    }

    @DeleteMapping("/{playlistId}/members/me")
    public PlaylistResponse leavePlaylist (Authentication authentication, @PathVariable Long playlistId) {
        return playlistService.leavePlaylist(authentication.getName(), playlistId);
    }

    @PutMapping("/{playlistId}/songs/{songId}")
    public PlaylistResponse addSong (Authentication authentication, @PathVariable Long playlistId, @PathVariable Long songId) {
        return playlistService.addSong(authentication.getName(), playlistId, songId);
    }

    @DeleteMapping("/{playlistId}/songs/{songId}")
    public PlaylistResponse removeSong (Authentication authentication, @PathVariable Long playlistId, @PathVariable Long songId) {
        return playlistService.removeSong(authentication.getName(), playlistId, songId);
    }

    @PutMapping("/{playlistId}/songs/{songId}/vote")
    public PlaylistResponse voteForSong (Authentication authentication, @PathVariable Long playlistId, @PathVariable Long songId) {
        return playlistService.voteForSong(authentication.getName(), playlistId, songId);
    }

    @DeleteMapping("/{playlistId}/songs/{songId}/vote")
    public PlaylistResponse removeSongVote (Authentication authentication, @PathVariable Long playlistId, @PathVariable Long songId) {
        return playlistService.removeSongVote(authentication.getName(), playlistId, songId);
    }

    private PlaylistType parsePlaylistType (String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return PlaylistType.fromDbValue(type.trim());
    }

    @GetMapping("/generated")
    public List<GeneratedPlaylistSummaryResponse> getGeneratedPlaylists() {
        return playlistService.getAvailableGeneratedPlaylists();
    }

    @GetMapping("/generated/{type}")
    public GeneratedPlaylistResponse generatePlaylist(Authentication authentication, @PathVariable GeneratedPlaylistType type) {
        return playlistService.generatePlaylist(authentication.getName(), type);
    }
}
