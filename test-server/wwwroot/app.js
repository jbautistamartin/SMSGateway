'use strict';

const App = (() => {

    // ── Estado ────────────────────────────────────────────────────────────────

    let count = 0;

    // ── DOM ───────────────────────────────────────────────────────────────────

    const $ = id => document.getElementById(id);

    const els = {
        statusDot:  $('status-dot'),
        statusText: $('status-text'),
        counter:    $('counter'),
        empty:      $('empty'),
        list:       $('list'),
        btnTest:    $('btn-test'),
    };

    // ── Renderizado ───────────────────────────────────────────────────────────

    function addSms(sms) {
        count++;

        els.empty.style.display = 'none';
        els.list.classList.add('visible');

        const receivedTime = new Date(sms.recibidoEn)
            .toLocaleTimeString('es-ES', { hour12: false });

        const card = document.createElement('li');
        card.className = 'sms-card new';
        card.innerHTML = `
            <div class="sms-meta">
                <span class="sms-index">#${count}</span>
                <span class="sms-phone">${escHtml(sms.telefono)}</span>
                ${sms.fecha ? `<span class="sms-date">${escHtml(sms.fecha)}</span>` : ''}
                <span class="sms-received">${receivedTime}</span>
            </div>
            <div class="sms-body">${escHtml(sms.mensaje)}</div>
        `;

        els.list.insertBefore(card, els.list.firstChild);
        setTimeout(() => card.classList.remove('new'), 2000);
        updateCounter();
    }

    function updateCounter() {
        els.counter.textContent = `${count} mensaje${count !== 1 ? 's' : ''}`;
    }

    function escHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function escAttr(str) {
        return String(str).replace(/'/g, '&#39;');
    }

    // ── Botón de prueba ───────────────────────────────────────────────────────

    async function sendTest() {
        const btn = els.btnTest;
        btn.disabled    = true;
        btn.textContent = 'Enviando…';

        const params = new URLSearchParams({
            mensaje:  'SMS de prueba desde el servidor',
            telefono: '+34000000000',
            fecha:    new Date().toISOString()
        });

        try {
            const res = await fetch(`/sms?${params}`);
            if (res.ok) {
                btn.textContent = '✓ Enviado';
                setTimeout(() => {
                    btn.textContent = 'Enviar prueba';
                    btn.disabled    = false;
                }, 2000);
            } else {
                btn.textContent = `✗ HTTP ${res.status}`;
                btn.disabled    = false;
            }
        } catch {
            btn.textContent = '✗ Error de red';
            btn.disabled    = false;
        }
    }

    // ── SSE ───────────────────────────────────────────────────────────────────

    function setStatus(state, text) {
        els.statusDot.className    = state;
        els.statusText.textContent = text;
    }

    function connect() {
        setStatus('', 'Conectando…');

        const es = new EventSource('/events');

        es.onopen = () => setStatus('connected', 'Conectado');

        es.onmessage = ({ data }) => {
            try { addSms(JSON.parse(data)); }
            catch { /* pings SSE — ignorar */ }
        };

        es.onerror = () => {
            setStatus('disconnected', 'Reconectando…');
            es.close();
            setTimeout(connect, 3000);
        };
    }

    // ── Modal de ayuda ────────────────────────────────────────────────────────

    const overlay     = $('modal-overlay');
    const helpLoading = $('help-loading');
    const helpContent = $('help-content');
    let   infoLoaded  = false;

    function openHelp() {
        overlay.classList.add('open');
        if (!infoLoaded) loadInfo();
    }

    function closeHelp() {
        overlay.classList.remove('open');
    }

    async function loadInfo() {
        try {
            const res  = await fetch('/info');
            const data = await res.json();

            helpLoading.style.display   = 'none';
            helpContent.style.display   = 'flex';
            helpContent.style.flexDirection = 'column';
            helpContent.style.gap       = '16px';
            helpContent.innerHTML       = renderHelp(data);
            infoLoaded = true;
        } catch {
            helpLoading.textContent = 'No se pudo obtener la información del servidor.';
        }
    }

    function renderHelp(data) {
        const sections = [];

        // IPs de red (para móvil en la misma WiFi)
        if (data.plantillas?.length > 0) {
            const blocks = data.plantillas.map(p => urlBlock(p.host, p)).join('');
            sections.push(`
                <div class="help-section">
                    <div class="help-section-title">📱 Desde el móvil (misma red WiFi)</div>
                    ${blocks}
                </div>
            `);
        }

        // Localhost
        if (data.localhost) {
            sections.push(`
                <div class="help-section">
                    <div class="help-section-title">💻 Desde este equipo (localhost)</div>
                    ${urlBlock('localhost', data.localhost)}
                </div>
            `);
        }

        // Ejemplos listos para pegar en el navegador
        const allHosts = [...(data.plantillas ?? []), data.localhost].filter(Boolean);
        if (allHosts.length > 0) {
            const ejemplos = allHosts.map(p => exampleBlock(p)).join('');
            sections.push(`
                <div class="help-section">
                    <div class="help-section-title">🌐 Ejemplos listos para el navegador</div>
                    <p class="help-intro" style="margin-bottom:8px">
                        Pega cualquiera de estas URLs en la barra de direcciones para simular un SMS sin la app Android.
                    </p>
                    ${ejemplos}
                </div>
            `);
        }

        // Nota sobre marcadores
        sections.push(`
            <div class="help-note">
                <strong>Marcadores disponibles en la plantilla:</strong><br>
                <code>{mensaje}</code> → texto del SMS &nbsp;·&nbsp; <strong>obligatorio</strong><br>
                <code>{telefono}</code> → número del remitente &nbsp;·&nbsp; opcional<br>
                <code>{fecha}</code> → timestamp ISO-8601 UTC &nbsp;·&nbsp; opcional
            </div>
        `);

        return sections.join('');
    }

    function exampleBlock(p) {
        const mensaje  = encodeURIComponent('SMS de prueba');
        const telefono = encodeURIComponent('+34612345678');
        const fecha    = encodeURIComponent(new Date().toISOString());

        function buildUrl(template) {
            return template
                .replace(encodeURIComponent('{mensaje}'),  mensaje)
                .replace('{mensaje}',  mensaje)
                .replace(encodeURIComponent('{telefono}'), telefono)
                .replace('{telefono}', telefono)
                .replace(encodeURIComponent('{fecha}'),    fecha)
                .replace('{fecha}',    fecha);
        }

        const row = (url) => `
            <div class="url-block-row">
                <span class="url-text">${escHtml(url)}</span>
                <button class="btn-copy" onclick="App.copy(this,'${escAttr(url)}')">Copiar</button>
            </div>
        `;

        const httpUrl  = buildUrl(p.http.urlCompleta);
        const httpsUrl = buildUrl(p.https.urlCompleta);

        return `
            <div class="url-block">
                <div class="url-block-host">🖥 ${escHtml(p.host)}</div>
                <div class="url-block-label">HTTP</div>
                ${row(httpUrl)}
                <div class="url-block-label">HTTPS</div>
                ${row(httpsUrl)}
            </div>
        `;
    }

    function urlBlock(label, p) {
        const rows = (scheme, urls) => `
            <div class="url-block-label">${scheme} — URL completa</div>
            <div class="url-block-row">
                <span class="url-text">${escHtml(urls.urlCompleta)}</span>
                <button class="btn-copy" onclick="App.copy(this,'${escAttr(urls.urlCompleta)}')">Copiar</button>
            </div>
            <div class="url-block-label">${scheme} — solo mensaje</div>
            <div class="url-block-row">
                <span class="url-text">${escHtml(urls.urlMinima)}</span>
                <button class="btn-copy" onclick="App.copy(this,'${escAttr(urls.urlMinima)}')">Copiar</button>
            </div>
        `;

        return `
            <div class="url-block">
                <div class="url-block-host">🖥 ${escHtml(label)}</div>
                ${rows('HTTP',  p.http)}
                ${rows('HTTPS', p.https)}
            </div>
        `;
    }

    function copy(btn, text) {
        navigator.clipboard.writeText(text).then(() => {
            btn.textContent = '✓ Copiado';
            btn.classList.add('copied');
            setTimeout(() => {
                btn.textContent = 'Copiar';
                btn.classList.remove('copied');
            }, 2000);
        });
    }

    // ── API pública ───────────────────────────────────────────────────────────

    function clear() {
        count = 0;
        els.list.innerHTML = '';
        els.list.classList.remove('visible');
        els.empty.style.display = 'flex';
        updateCounter();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    connect();

    return { clear, sendTest, openHelp, closeHelp, copy };

})();
