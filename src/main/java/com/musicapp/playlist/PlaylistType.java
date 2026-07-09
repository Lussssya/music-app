package com.musicapp.playlist;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.musicapp.common.BadRequestException;

public enum PlaylistType {
    PRIVATE("private"),
    PUBLIC("public"),
    SHARED("shared");

    private final String dbValue;

    PlaylistType (String dbValue) {
        this.dbValue = dbValue;
    }

    @JsonValue
    public String dbValue () {
        return dbValue;
    }

    @JsonCreator
    public static PlaylistType fromDbValue (String value) {
        for (PlaylistType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new BadRequestException("Unknown playlist type: " + value);
    }
}
