// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1

using System.Text.Json;
using System.Text.Json.Serialization;
using TestServer.Models;
using TestServer.Services;

namespace TestServer.Endpoints;

public static class SmsEndpoints
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy        = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition      = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented               = false
    };

    public static void MapSmsEndpoints(this WebApplication app)
    {
        // Recibe un SMS desde la app Android
        app.MapGet("/sms", ReceiveSms)
           .WithName("ReceiveSms")
           .WithDescription("Recibe un SMS desde la app Android y lo difunde a los navegadores conectados.");

        // Stream SSE al que se suscriben los navegadores
        app.MapGet("/events", StreamEvents)
           .WithName("SseStream")
           .WithDescription("Server-Sent Events: stream en tiempo real de SMS recibidos.");

        // Info del servidor: IPs locales y plantillas de URL listas para copiar
        app.MapGet("/info", ServerInfo)
           .WithName("Info");

        // Estado del servidor (útil para health-checks desde la app)
        app.MapGet("/status", (SmsHub hub) => Results.Ok(new
        {
            ok      = true,
            clients = hub.ClientCount,
            time    = DateTimeOffset.Now
        }))
        .WithName("Status");
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    // Puertos fijos configurados en appsettings.json
    private const int PuertoHttp  = 5000;
    private const int PuertoHttps = 5001;

    private static IResult ServerInfo()
    {
        // IPs IPv4 de la máquina (excluye loopback)
        var ips = System.Net.Dns.GetHostAddresses(System.Net.Dns.GetHostName())
            .Where(ip => ip.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork
                      && !System.Net.IPAddress.IsLoopback(ip))
            .Select(ip => ip.ToString())
            .ToList();

        static object Plantillas(string host) => new
        {
            host,
            http = new
            {
                urlCompleta = $"http://{host}:{PuertoHttp}/sms?mensaje={{mensaje}}&telefono={{telefono}}&fecha={{fecha}}",
                urlMinima   = $"http://{host}:{PuertoHttp}/sms?mensaje={{mensaje}}"
            },
            https = new
            {
                urlCompleta = $"https://{host}:{PuertoHttps}/sms?mensaje={{mensaje}}&telefono={{telefono}}&fecha={{fecha}}",
                urlMinima   = $"https://{host}:{PuertoHttps}/sms?mensaje={{mensaje}}"
            }
        };

        return Results.Ok(new
        {
            puertoHttp  = PuertoHttp,
            puertoHttps = PuertoHttps,
            ips,
            plantillas  = ips.Select(Plantillas).ToList(),
            localhost   = Plantillas("localhost")
        });
    }

    private static async Task<IResult> ReceiveSms(
        string?              mensaje,
        string?              telefono,
        string?              fecha,
        SmsHub               hub,
        ILogger<SmsHub>      logger)
    {
        if (string.IsNullOrWhiteSpace(mensaje))
            return Results.BadRequest(new { error = "El parámetro 'mensaje' es obligatorio." });

        var sms = new SmsEntry(
            Telefono:   telefono?.Trim() ?? "(desconocido)",
            Mensaje:    mensaje.Trim(),
            Fecha:      fecha?.Trim()    ?? string.Empty,
            RecibidoEn: DateTimeOffset.Now
        );

        await hub.BroadcastAsync(sms);

        logger.LogInformation(
            "SMS recibido · de: {Telefono} · texto: {Preview}",
            sms.Telefono,
            sms.Mensaje[..Math.Min(80, sms.Mensaje.Length)]);

        return Results.Ok(new { success = true });
    }

    private static async Task StreamEvents(
        HttpContext          ctx,
        SmsHub               hub,
        ILogger<SmsHub>      logger,
        CancellationToken    ct)
    {
        ctx.Response.Headers.Append("Content-Type",      "text/event-stream");
        ctx.Response.Headers.Append("Cache-Control",     "no-cache");
        ctx.Response.Headers.Append("X-Accel-Buffering", "no");   // evita buffering en nginx/proxies
        ctx.Response.Headers.Append("Connection",        "keep-alive");

        // Comentario SSE inicial para confirmar la conexión al navegador
        await ctx.Response.WriteAsync(": conectado\n\n", ct);
        await ctx.Response.Body.FlushAsync(ct);

        var channel = hub.Subscribe();
        logger.LogInformation("Cliente SSE conectado · activos: {Count}", hub.ClientCount);

        try
        {
            await foreach (var sms in channel.Reader.ReadAllAsync(ct))
            {
                var json = JsonSerializer.Serialize(sms, JsonOptions);
                await ctx.Response.WriteAsync($"data: {json}\n\n", ct);
                await ctx.Response.Body.FlushAsync(ct);
            }
        }
        catch (OperationCanceledException)
        {
            // El navegador cerró la conexión: comportamiento normal
        }
        finally
        {
            hub.Unsubscribe(channel);
            logger.LogInformation("Cliente SSE desconectado · activos: {Count}", hub.ClientCount);
        }
    }
}
