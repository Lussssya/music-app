package com.musicapp.auth;

public record CsrfTokenResponse(String headerName, String token) {
}
