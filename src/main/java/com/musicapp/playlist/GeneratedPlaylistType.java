package com.musicapp.playlist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum GeneratedPlaylistType {
    DAILY_REWIND("Daily Rewind", "Your most played songs today", Duration.ofDays(1)),
    WEEKLY_REWIND("Weekly Rewind", "Your top songs this week", Duration.ofDays(7)),
    ALL_TIME_REWIND("All Time Rewind", "Your all-time favorite songs", Duration.ofDays(7)),
    FORGOTTEN_GEMS("Forgotten Gems", "Songs you loved but haven't played recently", Duration.ofDays(7)),
    COMFORT_SONGS("Comfort Songs", "Your most played songs this year", Duration.ofDays(7)),
    NO_SKIPS("No Skips", "Songs you almost never skip", Duration.ofDays(7)),
    HIDDEN_FAVOURITES("Hidden Favourites", "Songs you stream often but never liked", Duration.ofDays(7)),
    GENRE_MIX("Genre Mix", "Top songs from your favorite genres", Duration.ofDays(7)),
    REDISCOVER("Rediscover", "Songs you loved 6-12 months ago", Duration.ofDays(7));

    private final String displayName;
    private final String description;
    private final Duration cacheDuration;
}
