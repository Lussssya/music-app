package com.musicapp.playlist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GeneratedPlaylistType {
    DAILY_REWIND("Daily Rewind", "Your most played songs today"),
    WEEKLY_REWIND("Weekly Rewind", "Your top songs this week"),
    ALL_TIME_REWIND("All Time Rewind", "Your all-time favorite songs"),
    FORGOTTEN_GEMS("Forgotten Gems", "Songs you loved but haven't played recently"),
    COMFORT_SONGS("Comfort Songs", "Your most played songs this year"),
    NO_SKIPS("No Skips", "Songs you almost never skip"),
    HIDDEN_FAVOURITES("Hidden Favourites", "Songs you stream often but never liked"),
    GENRE_MIX("Genre Mix", "Top songs from your favorite genres"),
    REDISCOVER("Rediscover", "Songs you loved 6-12 months ago")
    ;

    private final String displayName;
    private final String description;
}