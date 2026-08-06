package com.musicapp.playlist;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class PlaylistTypeConverter implements Converter<String, PlaylistType> {

    @Override
    public PlaylistType convert (String source) {
        if (source.isBlank()) {
            return null;
        }

        return PlaylistType.fromDbValue(source.trim());
    }
}