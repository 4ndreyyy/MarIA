package com.maria.model;

/** Datos de identidad necesarios mientras una cuenta tiene sesión iniciada. */
public record UsuarioSesion(
        String idUsuario,
        String username,
        String idConfig,
        String idTipos
) {
}
