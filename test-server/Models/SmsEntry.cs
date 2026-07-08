// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1

namespace TestServer.Models;

/// <summary>
/// Representa un SMS recibido por la app Android y enviado a este servidor de pruebas.
/// </summary>
/// <param name="Telefono">Número del remitente. Puede estar ausente si la app no lo envía.</param>
/// <param name="Mensaje">Texto del SMS. Obligatorio.</param>
/// <param name="Fecha">Timestamp original del SMS en formato ISO-8601 (opcional).</param>
/// <param name="RecibidoEn">Momento exacto en que este servidor recibió la petición.</param>
public record SmsEntry(
    string         Telefono,
    string         Mensaje,
    string         Fecha,
    DateTimeOffset RecibidoEn
);
