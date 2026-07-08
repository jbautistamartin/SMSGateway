// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

using TestServer.Endpoints;
using TestServer.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton<SmsHub>();
builder.Services.AddSingleton<LogFileService>();

var app = builder.Build();

app.UseDefaultFiles();   // sirve index.html automáticamente en /
app.UseStaticFiles();    // sirve wwwroot/

app.MapSmsEndpoints();

// Restaurar historial desde disco antes de aceptar peticiones
var logFile = app.Services.GetRequiredService<LogFileService>();
var hub     = app.Services.GetRequiredService<SmsHub>();

var historialPrevio = await logFile.CargarHistorialAsync();
if (historialPrevio.Count > 0)
{
    hub.InicializarHistorial(historialPrevio);
    app.Logger.LogInformation("Historial restaurado: {Count} SMS previos", historialPrevio.Count);
}

app.Logger.LogInformation("Historial de SMS → {Ruta}", logFile.RutaJson);

app.Run();
