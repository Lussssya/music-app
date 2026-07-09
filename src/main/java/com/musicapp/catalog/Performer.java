package com.musicapp.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "performer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performer_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String nickname;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "performer_type", nullable = false, columnDefinition = "performer_type")
    private PerformerType performerType;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "picture_url")
    private String pictureUrl;
}
