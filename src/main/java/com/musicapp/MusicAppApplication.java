package com.musicapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MusicAppApplication {

    public static void main (String[] args) {
        SpringApplication.run(MusicAppApplication.class, args);
    }
}
