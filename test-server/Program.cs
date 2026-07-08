// SMS Gateway — Servidor de pruebas
// Copyright © 2026 Capicua · José Luis Bautista Martín
// Licencia: GNU Lesser General Public License v2.1
// https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html

using TestServer.Endpoints;
using TestServer.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddSingleton<SmsHub>();

var app = builder.Build();

app.UseDefaultFiles();   // sirve index.html automáticamente en /
app.UseStaticFiles();    // sirve wwwroot/

app.MapSmsEndpoints();

app.Run();
