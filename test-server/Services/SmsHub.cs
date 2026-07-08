// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1

using System.Threading.Channels;
using TestServer.Models;

namespace TestServer.Services;

/// <summary>
/// Servicio singleton que difunde cada SMS recibido a todos los navegadores
/// conectados mediante Server-Sent Events.
///
/// Cada cliente SSE obtiene su propio <see cref="Channel{T}"/> al suscribirse.
/// Cuando llega un SMS se escribe en todos los canales activos.
/// </summary>
public sealed class SmsHub
{
    private readonly List<Channel<SmsEntry>> _clients = [];
    private readonly object _lock = new();

    /// <summary>Registra un nuevo cliente SSE y devuelve su canal de lectura.</summary>
    public Channel<SmsEntry> Subscribe()
    {
        var channel = Channel.CreateUnbounded<SmsEntry>(
            new UnboundedChannelOptions { SingleReader = true });

        lock (_lock)
            _clients.Add(channel);

        return channel;
    }

    /// <summary>Elimina el canal del cliente y lo cierra para que el stream SSE termine limpiamente.</summary>
    public void Unsubscribe(Channel<SmsEntry> channel)
    {
        lock (_lock)
            _clients.Remove(channel);

        channel.Writer.TryComplete();
    }

    /// <summary>Envía el SMS a todos los canales activos en ese momento.</summary>
    public async Task BroadcastAsync(SmsEntry sms)
    {
        List<Channel<SmsEntry>> snapshot;
        lock (_lock)
            snapshot = [.._clients];

        foreach (var channel in snapshot)
            await channel.Writer.WriteAsync(sms);
    }

    /// <summary>Número de navegadores actualmente conectados.</summary>
    public int ClientCount { get { lock (_lock) return _clients.Count; } }
}
