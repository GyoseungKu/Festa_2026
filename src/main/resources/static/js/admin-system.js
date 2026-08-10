(() => {
    "use strict";

    const history = [];
    const byId = (id) => document.getElementById(id);
    const text = (id, value) => { const node = byId(id); if (node) node.textContent = value; };
    const number = (value, fallback = "-") => value == null ? fallback : new Intl.NumberFormat("ko-KR").format(value);
    const percent = (value) => value == null ? "-" : `${Number(value).toFixed(1)}%`;
    const bytes = (value) => {
        if (value == null) return "-";
        const units = ["B", "KB", "MB", "GB", "TB"];
        let amount = Number(value), unit = 0;
        while (amount >= 1024 && unit < units.length - 1) { amount /= 1024; unit++; }
        return `${amount.toFixed(unit < 2 ? 0 : 1)} ${units[unit]}`;
    };
    const duration = (seconds) => {
        if (seconds == null) return "-";
        const days = Math.floor(seconds / 86400);
        const hours = Math.floor((seconds % 86400) / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        return days > 0 ? `${days}일 ${hours}시간` : `${hours}시간 ${minutes}분`;
    };

    function render(snapshot) {
        text("overall-status", statusLabel(snapshot.status));
        byId("overall-dot").className = `health-dot is-${String(snapshot.status).toLowerCase()}`;
        text("sampled-at", new Date(snapshot.sampledAt).toLocaleTimeString("ko-KR"));

        const p = snapshot.presence;
        text("active-sessions", number(p.activeSessions));
        text("presence-note", `${p.ttlSeconds}초 내 heartbeat 기준`);
        text("route-total", `${number(p.activeSessions)} sessions`);
        renderRoutes(p.routes);

        const runtime = snapshot.runtime;
        text("process-cpu", percent(runtime.processCpuPercent));
        text("system-cpu", `서버 전체 ${percent(runtime.systemCpuPercent)}`);
        text("heap-usage", percent(runtime.heapUsagePercent));
        text("heap-bytes", `${bytes(runtime.heapUsedBytes)} / ${bytes(runtime.heapMaxBytes)}`);
        text("uptime", duration(runtime.uptimeSeconds));
        text("thread-count", `스레드 ${number(runtime.liveThreads)} · peak ${number(runtime.peakThreads)}`);
        text("disk-free", `${bytes(runtime.diskFreeBytes)} / ${bytes(runtime.diskTotalBytes)}`);
        text("system-load", runtime.systemLoadAverage == null ? "-" : Number(runtime.systemLoadAverage).toFixed(2));

        const http = snapshot.http;
        text("requests-minute", number(http.requestsLastMinute));
        text("requests-second", `${Number(http.requestsPerSecond).toFixed(2)} req/s`);
        text("latency-p95", `${number(http.p95LatencyMs)} ms`);
        text("latency-average", `평균 ${Number(http.averageLatencyMs).toFixed(1)} ms`);
        text("error-rate", percent(http.errorRatePercent));
        text("error-count", `4xx ${number(http.clientErrors)} · 5xx ${number(http.serverErrors)}`);
        text("in-flight", number(http.inFlight));

        const pool = snapshot.connectionPool;
        text("pool-active", pool.active == null ? "-" : `${number(pool.active)} / ${number(pool.max)}`);
        text("pool-detail", `idle ${number(pool.idle)} · pending ${number(pool.pending)}`);

        renderDatabase(snapshot.database);
        renderQueues(snapshot.queues);
        addChartPoint(runtime.processCpuPercent, runtime.heapUsagePercent);
        byId("monitor-error").hidden = true;
    }

    function statusLabel(status) {
        return ({HEALTHY: "정상", WARNING: "주의", CRITICAL: "위험", LIMITED: "일부 제한"})[status] || status;
    }

    function renderRoutes(routes) {
        const body = byId("route-rows");
        body.replaceChildren();
        if (!routes || routes.length === 0) {
            const row = body.insertRow();
            const cell = row.insertCell(); cell.colSpan = 2; cell.textContent = "활성 heartbeat가 없습니다.";
            return;
        }
        routes.forEach((route) => {
            const row = body.insertRow();
            row.insertCell().textContent = route.route;
            row.insertCell().textContent = number(route.activeSessions);
        });
    }

    function renderDatabase(db) {
        text("db-status", db.status);
        byId("db-status").className = `status-pill is-${String(db.status).toLowerCase().replace("_", "-")}`;
        text("db-latency", `${number(db.latencyMs)} ms`);
        text("db-connections", db.connections == null ? "-" : `${number(db.connections)} / ${number(db.maxConnections)}`);
        text("db-running", number(db.runningThreads));
        text("db-qps", db.queriesPerSecond == null ? "-" : Number(db.queriesPerSecond).toFixed(2));
        text("db-hit-rate", percent(db.bufferPoolHitRatePercent));
        text("db-incidents", db.newSlowQueries == null ? "-" : `${number(db.newSlowQueries)} / ${number(db.newDeadlocks)}`);
        byId("db-limited").hidden = db.status !== "UP_LIMITED";
    }

    function renderQueues(q) {
        queue("api", q.apiRequestQueueSize, q.apiRequestQueueCapacity, q.apiRequestDroppedTotal);
        queue("event", q.frontendEventQueueSize, q.frontendEventQueueCapacity, q.frontendEventDroppedTotal);
    }

    function queue(prefix, size, capacity, dropped) {
        const progress = byId(`${prefix}-queue`);
        progress.max = Math.max(1, capacity);
        progress.value = size;
        text(`${prefix}-queue-text`, `${number(size)} / ${number(capacity)}`);
        text(`${prefix}-dropped`, `누적 유실 ${number(dropped)}`);
    }

    function addChartPoint(cpu, heap) {
        history.push({cpu: cpu == null ? null : Number(cpu), heap: heap == null ? null : Number(heap)});
        if (history.length > 60) history.shift();
        drawChart();
    }

    function drawChart() {
        const canvas = byId("resource-chart");
        const width = canvas.clientWidth || 700;
        const height = 240;
        const ratio = window.devicePixelRatio || 1;
        canvas.width = width * ratio; canvas.height = height * ratio;
        const ctx = canvas.getContext("2d"); ctx.scale(ratio, ratio);
        ctx.clearRect(0, 0, width, height);
        ctx.strokeStyle = "rgba(255,255,255,.12)"; ctx.lineWidth = 1;
        [0, 25, 50, 75, 100].forEach((value) => {
            const y = 12 + (100 - value) / 100 * (height - 28);
            ctx.beginPath(); ctx.moveTo(36, y); ctx.lineTo(width - 8, y); ctx.stroke();
            ctx.fillStyle = "rgba(255,255,255,.48)"; ctx.font = "11px sans-serif"; ctx.fillText(`${value}%`, 2, y + 4);
        });
        line("cpu", "#ffacd9"); line("heap", "#a6d8ff");
        function line(key, color) {
            ctx.beginPath(); ctx.strokeStyle = color; ctx.lineWidth = 2.5; ctx.shadowColor = color; ctx.shadowBlur = 8;
            let started = false;
            history.forEach((point, index) => {
                if (point[key] == null) { started = false; return; }
                const x = 36 + index / Math.max(1, history.length - 1) * (width - 44);
                const y = 12 + (100 - Math.max(0, Math.min(100, point[key]))) / 100 * (height - 28);
                if (!started) { ctx.moveTo(x, y); started = true; } else ctx.lineTo(x, y);
            });
            ctx.stroke(); ctx.shadowBlur = 0;
        }
    }

    async function refresh() {
        if (document.hidden) return;
        try {
            const response = await fetch("/admin/system/snapshot", {credentials: "same-origin", headers: {Accept: "application/json"}});
            if (response.status === 401) { location.assign("/admin/login"); return; }
            if (response.status === 403) { location.assign("/admin"); return; }
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            render(await response.json());
        } catch (error) {
            byId("monitor-error").hidden = false;
            text("overall-status", "연결 오류");
            byId("overall-dot").className = "health-dot is-critical";
        }
    }

    window.addEventListener("resize", drawChart);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(); });
    refresh();
    window.setInterval(refresh, 5000);
})();
