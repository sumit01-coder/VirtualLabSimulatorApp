<?php
$page_title = "DDoS Monitor";
require_once 'includes/header.php';
if (empty($_SESSION['ddos_csrf'])) {
    $_SESSION['ddos_csrf'] = bin2hex(random_bytes(16));
}
$ddos_csrf = (string)$_SESSION['ddos_csrf'];
?>

<style>
:root {
    --danger:  #ef4444;
    --warn:    #f59e0b;
    --safe:    #10b981;
    --info:    #3b82f6;
    --purple:  #8b5cf6;
    --dark:    #1e293b;
    --card:    #fff;
    --border:  #e2e8f0;
    --muted:   #64748b;
    --text:    #1e293b;
}

.ddos-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 14px; margin-bottom: 20px; }
.stat-card {
    background: var(--card); border: 1px solid var(--border); border-radius: 12px;
    padding: 16px; display: flex; flex-direction: column; gap: 4px;
    box-shadow: 0 1px 4px rgba(0,0,0,0.06); transition: transform .2s;
    position: relative; overflow: hidden;
}
.stat-card::before { content:''; position:absolute; top:0; left:0; width:4px; height:100%; background:var(--accent,#3b82f6); border-radius:12px 0 0 12px; }
.stat-card:hover { transform: translateY(-2px); }
.stat-label { font-size: .72rem; color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: .6px; }
.stat-val   { font-size: 1.7rem; font-weight: 800; color: var(--text); line-height: 1.1; }
.stat-sub   { font-size: .72rem; color: var(--muted); }

.ddos-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
@media(max-width:900px){.ddos-row{grid-template-columns:1fr;}}

.panel {
    background: var(--card); border: 1px solid var(--border); border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0,0,0,0.06); overflow: hidden;
}
.panel-head {
    padding: 12px 16px; font-weight: 700; font-size: .82rem;
    text-transform: uppercase; letter-spacing: .6px; color: var(--muted);
    border-bottom: 1px solid var(--border); display: flex; align-items: center;
    justify-content: space-between; background: #f8fafc;
}
.panel-head .badge { font-size:.7rem; padding:2px 8px; border-radius:20px; font-weight:700; }
.badge-red  { background:#fee2e2; color:var(--danger); }
.badge-grn  { background:#d1fae5; color:var(--safe); }
.badge-org  { background:#fef3c7; color:var(--warn); }
.live-dot { width:8px; height:8px; border-radius:50%; background:var(--safe); margin-right:6px;
    animation: pulse-dot 1.5s infinite; display:inline-block; }
@keyframes pulse-dot { 0%,100%{opacity:1}50%{opacity:.3} }

/* Chart canvas */
#rateChart { width:100% !important; height:180px !important; }

/* Tables */
.dd-table { width:100%; border-collapse:collapse; font-size:.82rem; }
.dd-table th { padding:8px 12px; text-align:left; font-size:.72rem; text-transform:uppercase; letter-spacing:.5px; color:var(--muted); background:#f8fafc; border-bottom:1px solid var(--border); position:sticky;top:0; }
.dd-table td { padding:8px 12px; border-bottom:1px solid #f1f5f9; vertical-align:middle; }
.dd-table tr:last-child td { border-bottom:none; }
.dd-table tr:hover td { background:#f8fafc; }
.tbl-wrap { max-height:280px; overflow-y:auto; }

/* RF badge */
.rf-badge { display:inline-block; padding:2px 8px; border-radius:20px; font-size:.7rem; font-weight:700; }
.rf-0 { background:#d1fae5; color:#065f46; }
.rf-1 { background:#fef3c7; color:#92400e; }
.rf-2 { background:#fee2e2; color:#991b1b; }

/* IP chip */
.ip-chip { font-family: monospace; font-size:.8rem; background:#f1f5f9; border-radius:4px; padding:1px 6px; }

/* action btns */
.act-btn { font-size:.72rem; padding:3px 9px; border:none; border-radius:6px; cursor:pointer; font-weight:600; transition:all .15s; }
.btn-unblock { background:#d1fae5; color:#065f46; }
.btn-unblock:hover { background:#a7f3d0; }
.btn-block   { background:#fee2e2; color:#991b1b; }
.btn-block:hover { background:#fecaca; }

/* Manual block bar */
.block-bar { display:flex; gap:8px; padding:10px 14px; border-top:1px solid var(--border); background:#f8fafc; }
.block-bar input { flex:1; border:1px solid var(--border); border-radius:6px; padding:5px 10px; font-size:.82rem; outline:none; }
.block-bar input:focus { border-color:var(--info); }
.block-bar select { border:1px solid var(--border); border-radius:6px; padding:5px 8px; font-size:.82rem; }

/* Auto-refresh countdown */
.refresh-bar { display:flex; align-items:center; gap:10px; margin-bottom:14px; font-size:.8rem; color:var(--muted); }
.refresh-bar progress { flex:1; height:4px; border-radius:2px; }

/* Toasts */
#toast { position:fixed; bottom:20px; right:20px; background:#1e293b; color:#fff; padding:10px 18px; border-radius:8px; font-size:.85rem; z-index:9999; display:none; }
</style>

<!-- Auto-refresh bar -->
<div class="refresh-bar">
    <span><span class="live-dot"></span>Live Monitor - refreshes every <strong>5s</strong></span>
    <progress id="refreshProg" max="5" value="5"></progress>
    <span id="lastUpdate" style="min-width:120px">-</span>
    <button onclick="fetchAll()" style="font-size:.75rem;padding:3px 10px;border:1px solid #e2e8f0;border-radius:6px;background:#fff;cursor:pointer;">Refresh now</button>
</div>

<!-- Stat cards -->
<div class="ddos-grid" id="statCards">
    <?php
    $cards = [
        ['id'=>'c_reqs_5min',   'label'=>'Requests (5 min)', 'sub'=>'all traffic',        'accent'=>'#3b82f6'],
        ['id'=>'c_reqs_60s',    'label'=>'Requests (60s)',   'sub'=>'rolling window',     'accent'=>'#8b5cf6'],
        ['id'=>'c_reqs_10s',    'label'=>'Burst (10s)',      'sub'=>'spike detector',     'accent'=>'#f59e0b'],
        ['id'=>'c_unique_ips',  'label'=>'Unique IPs',       'sub'=>'last 5 minutes',     'accent'=>'#06b6d4'],
        ['id'=>'c_blocked_now', 'label'=>'Blocked IPs',      'sub'=>'currently active',   'accent'=>'#ef4444'],
        ['id'=>'c_error_reqs',  'label'=>'Error Requests',   'sub'=>'4xx / 5xx (5 min)',  'accent'=>'#f97316'],
    ];
    foreach($cards as $c): ?>
    <div class="stat-card" style="--accent:<?= $c['accent'] ?>">
        <div class="stat-label"><?= $c['label'] ?></div>
        <div class="stat-val" id="<?= $c['id'] ?>">–</div>
        <div class="stat-sub"><?= $c['sub'] ?></div>
    </div>
    <?php endforeach; ?>
</div>

<!-- Charts row -->
<div class="ddos-row">
    <div class="panel">
        <div class="panel-head">
            📈 Request Rate (last 2 min, per-second)
            <span class="badge badge-grn" id="rateStatus">live</span>
        </div>
        <div style="padding:12px">
            <canvas id="rateChart"></canvas>
        </div>
    </div>

    <div class="panel">
        <div class="panel-head">🔴 Currently Blocked IPs <span class="badge badge-red" id="blockedCount">0</span></div>
        <div class="tbl-wrap">
            <table class="dd-table" id="blockedTable">
                <thead><tr>
                    <th>IP</th><th>Reason</th><th>Expires</th><th>Hits</th><th>Action</th>
                </tr></thead>
                <tbody id="blockedBody"><tr><td colspan="5" style="text-align:center;color:#94a3b8">Loading…</td></tr></tbody>
            </table>
        </div>
        <div class="block-bar">
            <input type="text" id="manualIP" placeholder="IP to block (e.g. 1.2.3.4)">
            <select id="manualDur">
                <option value="3600">1 hour</option>
                <option value="21600">6 hours</option>
                <option value="86400">24 hours</option>
            </select>
            <button class="act-btn btn-block" onclick="manualBlock()">🚫 Block IP</button>
        </div>
    </div>
</div>

<!-- Top IPs + Recent Requests row -->
<div class="ddos-row">
    <div class="panel">
        <div class="panel-head">🏆 Top IPs by Requests (5 min)</div>
        <div class="tbl-wrap">
            <table class="dd-table" id="topIPsTable">
                <thead><tr>
                    <th>IP</th><th>Reqs</th><th>Errors</th><th>URLs</th><th>RF Class</th><th>Action</th>
                </tr></thead>
                <tbody id="topIPsBody"><tr><td colspan="6" style="text-align:center;color:#94a3b8">Loading…</td></tr></tbody>
            </table>
        </div>
    </div>

    <div class="panel">
        <div class="panel-head">📋 Recent Requests (last 60s)</div>
        <div class="tbl-wrap">
            <table class="dd-table" id="recentTable">
                <thead><tr>
                    <th>Time</th><th>IP</th><th>Method</th><th>Endpoint</th><th>Err</th>
                </tr></thead>
                <tbody id="recentBody"><tr><td colspan="5" style="text-align:center;color:#94a3b8">Loading…</td></tr></tbody>
            </table>
        </div>
    </div>
</div>

<div id="toast"></div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
<script>
// ── Chart setup ───────────────────────────────────────────────────────────
const DDOS_CSRF = <?php echo json_encode($ddos_csrf); ?>;
const ctx = document.getElementById('rateChart').getContext('2d');
let rateLabels = [], rateData = [];
const rateChart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: rateLabels,
        datasets: [{
            label: 'Req/s',
            data: rateData,
            borderColor: '#3b82f6',
            backgroundColor: 'rgba(59,130,246,0.08)',
            borderWidth: 2,
            pointRadius: 0,
            fill: true,
            tension: 0.4,
        }]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: { legend: { display: false } },
        scales: {
            x: { display: false },
            y: { beginAtZero: true, grid: { color: '#f1f5f9' }, ticks: { font: { size: 10 } } }
        }
    }
});

// ── Helpers ───────────────────────────────────────────────────────────────
function esc(s) { const d = document.createElement('div'); d.textContent = s || '-'; return d.innerHTML; }
function toast(msg, ok=true) {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.style.background = ok ? '#065f46' : '#991b1b';
    t.style.display = 'block';
    setTimeout(() => t.style.display = 'none', 3000);
}
function rfBadge(cls) {
    const labels = ['SAFE','SUSPICIOUS','MALICIOUS'], classes = ['rf-0','rf-1','rf-2'];
    return `<span class="rf-badge ${classes[cls]}">${labels[cls]}</span>`;
}
function blockBtn(ip)   { return `<button class="act-btn btn-block"   onclick="quickBlock('${ip}')">Block</button>`; }
function unblockBtn(ip) { return `<button class="act-btn btn-unblock" onclick="unblock('${ip}')">Unblock</button>`; }
function timeDiff(dateStr) {
    const exp = new Date(dateStr), now = new Date();
    const s = Math.round((exp - now) / 1000);
    if (s <= 0) return 'expires soon';
    if (s < 60) return s + 's';
    if (s < 3600) return Math.round(s/60) + 'm';
    return Math.round(s/3600) + 'h';
}

// ── Fetch functions ───────────────────────────────────────────────────────
async function api(action, opts={}) {
    const url = 'ddos_api.php?action=' + action;
    try {
        opts.credentials = 'same-origin';
        const r = await fetch(url, opts);
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return await r.json();
    } catch(e) { console.error(action, e); return null; }
}

async function fetchOverview() {
    const d = await api('overview');
    if (!d) return;
    document.getElementById('c_reqs_5min').textContent   = d.reqs_5min.toLocaleString();
    document.getElementById('c_reqs_60s').textContent    = d.reqs_60s.toLocaleString();
    document.getElementById('c_reqs_10s').textContent    = d.reqs_10s.toLocaleString();
    document.getElementById('c_unique_ips').textContent  = d.unique_ips.toLocaleString();
    document.getElementById('c_blocked_now').textContent = d.blocked_now.toLocaleString();
    document.getElementById('c_error_reqs').textContent  = d.error_reqs.toLocaleString();
    document.getElementById('lastUpdate').textContent    = 'Updated: ' + new Date().toLocaleTimeString();
}

async function fetchBlocked() {
    const rows = await api('blocked');
    if (!rows) return;
    document.getElementById('blockedCount').textContent = rows.length;
    const tb = document.getElementById('blockedBody');
    if (!rows.length) { tb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8">No blocked IPs</td></tr>'; return; }
    tb.innerHTML = rows.map(r => `
        <tr>
            <td><span class="ip-chip">${esc(r.ip)}</span></td>
            <td>${esc(r.reason)}</td>
            <td>${timeDiff(r.blocked_until)}</td>
            <td>${esc(r.hit_count)}</td>
            <td>${unblockBtn(r.ip)}</td>
        </tr>`).join('');
}

async function fetchTopIPs() {
    const rows = await api('top_ips');
    if (!rows) return;
    const tb = document.getElementById('topIPsBody');
    if (!rows.length) { tb.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#94a3b8">No data yet</td></tr>'; return; }
    tb.innerHTML = rows.map(r => `
        <tr>
            <td><span class="ip-chip">${esc(r.ip)}</span></td>
            <td>${esc(r.total)}</td>
            <td>${esc(r.errors)}</td>
            <td>${esc(r.endpoints)}</td>
            <td>${rfBadge(+r.rf_class)}</td>
            <td>${r.rf_class >= 1 ? blockBtn(r.ip) : ''}</td>
        </tr>`).join('');
}

async function fetchRecent() {
    const rows = await api('recent');
    if (!rows) return;
    const tb = document.getElementById('recentBody');
    if (!rows.length) { tb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8">No recent requests</td></tr>'; return; }
    tb.innerHTML = rows.slice(0, 60).map(r => `
        <tr style="${r.is_error=='1'?'background:#fff7ed':''}">
            <td style="color:#94a3b8;white-space:nowrap">${esc((r.time_str||'').split(' ')[1])}</td>
            <td><span class="ip-chip">${esc(r.ip)}</span></td>
            <td><span style="font-size:.7rem;padding:1px 5px;border-radius:4px;background:${r.method==='POST'?'#dbeafe':'#f1f5f9'}">${esc(r.method)}</span></td>
            <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${esc(r.endpoint)}">${esc(r.endpoint)}</td>
            <td>${r.is_error=='1'?'<span style="color:#ef4444">●</span>':''}</td>
        </tr>`).join('');
}

async function fetchRateHistory() {
    const rows = await api('rate_history');
    if (!rows || !rows.length) return;
    rateLabels.length = 0;
    rateData.length = 0;
    rows.forEach(r => { rateLabels.push(r.second_bucket); rateData.push(+r.count); });
    rateChart.update('none');
}

// ── Actions ───────────────────────────────────────────────────────────────
async function unblock(ip) {
    if (!confirm('Unblock ' + ip + '?')) return;
    const d = await api('unblock', {method:'POST', body:new URLSearchParams({ip, csrf: DDOS_CSRF})});
    if (d && d.success) { toast('Unblocked ' + ip); fetchAll(); }
    else toast('Error unblocking ' + ip, false);
}

async function quickBlock(ip) {
    if (!confirm('Block ' + ip + ' for 1 hour?')) return;
    const d = await api('block', {method:'POST', body:new URLSearchParams({ip, duration:3600, csrf: DDOS_CSRF})});
    if (d && d.success) { toast('Blocked ' + ip); fetchAll(); }
    else toast('Error blocking ' + ip, false);
}

async function manualBlock() {
    const ip  = document.getElementById('manualIP').value.trim();
    const dur = document.getElementById('manualDur').value;
    if (!ip) { toast('Enter a valid IP', false); return; }
    const d = await api('block', {method:'POST', body:new URLSearchParams({ip, duration:dur, csrf: DDOS_CSRF})});
    if (d && d.success) { toast('Blocked ' + ip); document.getElementById('manualIP').value=''; fetchAll(); }
    else toast('Error: ' + (d?.error || 'unknown'), false);
}

// ── Auto-refresh ──────────────────────────────────────────────────────────
async function fetchAll() {
    const b = await api('bundle');
    if (b && b.overview) {
        const d = b.overview;
        document.getElementById('c_reqs_5min').textContent   = (d.reqs_5min ?? 0).toLocaleString();
        document.getElementById('c_reqs_60s').textContent    = (d.reqs_60s ?? 0).toLocaleString();
        document.getElementById('c_reqs_10s').textContent    = (d.reqs_10s ?? 0).toLocaleString();
        document.getElementById('c_unique_ips').textContent  = (d.unique_ips ?? 0).toLocaleString();
        document.getElementById('c_blocked_now').textContent = (d.blocked_now ?? 0).toLocaleString();
        document.getElementById('c_error_reqs').textContent  = (d.error_reqs ?? 0).toLocaleString();
        document.getElementById('lastUpdate').textContent    = 'Updated: ' + new Date((b.ts || (Date.now()/1000)) * 1000).toLocaleTimeString();

        const blockedRows = Array.isArray(b.blocked) ? b.blocked : [];
        document.getElementById('blockedCount').textContent = blockedRows.length;
        const blockedTb = document.getElementById('blockedBody');
        if (!blockedRows.length) blockedTb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8">No blocked IPs</td></tr>';
        else blockedTb.innerHTML = blockedRows.map(r => `
            <tr>
                <td><span class="ip-chip">${esc(r.ip)}</span></td>
                <td>${esc(r.reason)}</td>
                <td>${timeDiff(r.blocked_until)}</td>
                <td>${esc(r.hit_count)}</td>
                <td>${unblockBtn(r.ip)}</td>
            </tr>`).join('');

        const topRows = Array.isArray(b.top_ips) ? b.top_ips : [];
        const topTb = document.getElementById('topIPsBody');
        if (!topRows.length) topTb.innerHTML = '<tr><td colspan="6" style="text-align:center;color:#94a3b8">No data yet</td></tr>';
        else topTb.innerHTML = topRows.map(r => `
            <tr>
                <td><span class="ip-chip">${esc(r.ip)}</span></td>
                <td>${esc(r.total)}</td>
                <td>${esc(r.errors)}</td>
                <td>${esc(r.endpoints)}</td>
                <td>${rfBadge(+r.rf_class)}</td>
                <td>${r.rf_class >= 1 ? blockBtn(r.ip) : ''}</td>
            </tr>`).join('');

        const recentRows = Array.isArray(b.recent) ? b.recent : [];
        const recentTb = document.getElementById('recentBody');
        if (!recentRows.length) recentTb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:#94a3b8">No recent requests</td></tr>';
        else recentTb.innerHTML = recentRows.slice(0, 60).map(r => `
            <tr style="${r.is_error=='1'?'background:#fff7ed':''}">
                <td style="color:#94a3b8;white-space:nowrap">${esc((r.time_str||'').split(' ')[1])}</td>
                <td><span class="ip-chip">${esc(r.ip)}</span></td>
                <td><span style="font-size:.7rem;padding:1px 5px;border-radius:4px;background:${r.method==='POST'?'#dbeafe':'#f1f5f9'}">${esc(r.method)}</span></td>
                <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" title="${esc(r.endpoint)}">${esc(r.endpoint)}</td>
                <td>${r.is_error=='1'?'<span style="color:#ef4444">●</span>':''}</td>
            </tr>`).join('');

        const rateRows = Array.isArray(b.rate_history) ? b.rate_history : [];
        if (rateRows.length) {
            rateLabels.length = 0;
            rateData.length = 0;
            rateRows.forEach(r => { rateLabels.push(r.second_bucket); rateData.push(+r.count); });
            rateChart.update('none');
        }
        return;
    }

    await Promise.all([fetchOverview(), fetchBlocked(), fetchTopIPs(), fetchRecent(), fetchRateHistory()]);
}

let countdown = 5;
const prog = document.getElementById('refreshProg');
setInterval(() => {
    countdown--;
    prog.value = countdown;
    if (countdown <= 0) { countdown = 5; prog.value = 5; fetchAll(); }
}, 1000);

// Initial load
fetchAll();
</script>

<?php require_once 'includes/footer.php'; ?>
