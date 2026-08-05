package com.musicapp.playlist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum GeneratedPlaylistType {
    DAILY_REWIND(1, "Daily Rewind", "Your most played songs today", Duration.ofDays(1)),
    WEEKLY_REWIND(2, "Weekly Rewind", "Your top songs this week", Duration.ofDays(7)),
    ALL_TIME_REWIND(3, "All Time Rewind", "Your all-time favorite songs", Duration.ofDays(7)),
    FORGOTTEN_GEMS(4, "Forgotten Gems", "Songs you loved but haven't played recently", Duration.ofDays(7)),
    COMFORT_SONGS(5, "Comfort Songs", "Your most played songs this year", Duration.ofDays(7)),
    NO_SKIPS(6, "No Skips", "Songs you almost never skip", Duration.ofDays(7)),
    HIDDEN_FAVOURITES(7, "Hidden Favourites", "Songs you stream often but never liked", Duration.ofDays(7)),
    GENRE_MIX(8, "Genre Mix", "Top songs from your favorite genres", Duration.ofDays(7)),
    REDISCOVER(9, "Rediscover", "Songs you loved 6-12 months ago", Duration.ofDays(7));

    private final int lockCode;
    private final String displayName;
    private final String description;
    private final Duration cacheDuration;
}
