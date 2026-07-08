// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1

using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using TestServer.Models;

namespace TestServer.Services;

/// <summary>
/// Persiste el historial de SMS en <c>sms_historial.json</c> (JSON Lines,
/// un objeto por línea) junto al ejecutable.
///
/// <para>Al cargar, descarta automáticamente las entradas con más de 30 días
/// de antigüedad. El archivo se regenera compactado tras la purga.</para>
/// </summary>
public sealed class LogFileService
{
    private const int DiasVigencia = 30;

    private readonly string        _rutaJson;
    private readonly SemaphoreSlim _sem = new(1, 1);

    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        PropertyNamingPolicy   = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented          = false
    };

    public LogFileService(IWebHostEnvironment env)
    {
        _rutaJson = Path.Combine(env.ContentRootPath, "sms_historial.json");
    }

    public string RutaJson => _rutaJson;

    // ── Escritura ─────────────────────────────────────────────────────────────

    /// <summary>Añade el SMS al final del archivo.</summary>
    public async Task AgregarAsync(SmsEntry sms)
    {
        var linea = JsonSerializer.Serialize(sms, JsonOpts);
        await _sem.WaitAsync();
        try
        {
            await File.AppendAllTextAsync(_rutaJson, linea + Environment.NewLine, Encoding.UTF8);
        }
        finally { _sem.Release(); }
    }

    // ── Carga y purga al arrancar ─────────────────────────────────────────────

    /// <summary>
    /// Lee el archivo, descarta entradas con más de <see cref="DiasVigencia"/> días
    /// y, si hubo purga, reescribe el archivo compactado.
    /// Devuelve la lista vigente ordenada de más antiguo a más reciente.
    /// </summary>
    public async Task<List<SmsEntry>> CargarHistorialAsync()
    {
        if (!File.Exists(_rutaJson)) return [];

        var lineas  = await File.ReadAllLinesAsync(_rutaJson, Encoding.UTF8);
        var limite  = DateTimeOffset.Now.AddDays(-DiasVigencia);
        var vigente = new List<SmsEntry>();
        var hubo    = false;   // ¿se descartó alguna entrada?

        foreach (var linea in lineas)
        {
            if (string.IsNullOrWhiteSpace(linea)) continue;
            try
            {
                var entry = JsonSerializer.Deserialize<SmsEntry>(linea, JsonOpts);
                if (entry is null) continue;

                if (entry.RecibidoEn >= limite)
                    vigente.Add(entry);
                else
                    hubo = true;   // entrada purgada
            }
            catch { hubo = true; /* línea corrupta — descartar */ }
        }

        // Reescribir sin las entradas caducadas
        if (hubo)
            await ReescribirAsync(vigente);

        return vigente;
    }

    // ── Limpieza total ────────────────────────────────────────────────────────

    /// <summary>Vacía completamente el archivo.</summary>
    public async Task LimpiarAsync()
    {
        await _sem.WaitAsync();
        try
        {
            await File.WriteAllTextAsync(_rutaJson, string.Empty, Encoding.UTF8);
        }
        finally { _sem.Release(); }
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private async Task ReescribirAsync(IEnumerable<SmsEntry> entradas)
    {
        var sb = new StringBuilder();
        foreach (var e in entradas)
            sb.AppendLine(JsonSerializer.Serialize(e, JsonOpts));

        await _sem.WaitAsync();
        try
        {
            await File.WriteAllTextAsync(_rutaJson, sb.ToString(), Encoding.UTF8);
        }
        finally { _sem.Release(); }
    }
}
