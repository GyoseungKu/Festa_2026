// 실시간 탭은 3초마다 가벼운 커서만 확인하고, 실제 변경이 있을 때만 페이지를 갱신한다.
// 신고 수는 메시지 커서를 바꾸지 않으므로 신고 탭은 10초 간격으로 갱신한다.
(function () {
    var LIVE_INTERVAL_MS = 3000;
    var REPORTED_INTERVAL_MS = 10000;
    var STORAGE_KEY = 'bambooAutoRefresh';
    var root = document.getElementById('bamboo-admin');
    var button = document.getElementById('bamboo-auto-refresh');
    if (!root || !button) return;

    var tab = root.dataset.tab || 'LIVE';
    var currentCursor = Number(root.dataset.cursor || '0');
    var cursorUrl = root.dataset.cursorUrl;
    var timer = null;
    var checking = false;
    var inMemoryEnabled = tab === 'LIVE';

    function enabled() {
        try {
            var saved = sessionStorage.getItem(STORAGE_KEY);
            return saved === null ? tab === 'LIVE' : saved === 'on';
        } catch (unavailable) {
            return inMemoryEnabled;
        }
    }

    function remember(value) {
        inMemoryEnabled = value;
        try {
            sessionStorage.setItem(STORAGE_KEY, value ? 'on' : 'off');
        } catch (unavailable) {
            // 저장소를 못 쓰면 이번 페이지에서만 동작한다.
        }
    }

    function busy() {
        var active = document.activeElement;
        if (document.visibilityState !== 'visible') return true;
        if (!active) return false;
        var tag = active.tagName;
        return tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
    }

    async function tick() {
        if (checking || busy()) return;
        if (tab !== 'LIVE') {
            window.location.reload();
            return;
        }
        checking = true;
        try {
            var response = await fetch(cursorUrl, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'Accept': 'application/json' }
            });
            if (response.status === 401) {
                window.location.reload();
                return;
            }
            if (!response.ok) return;
            var payload = await response.json();
            if (Number(payload.cursor) > currentCursor) window.location.reload();
        } catch (ignored) {
            // 일시적 통신 장애에는 현재 화면을 유지하고 다음 주기에 다시 확인한다.
        } finally {
            checking = false;
        }
    }

    function apply(on) {
        if (timer !== null) window.clearInterval(timer);
        timer = on ? window.setInterval(tick,
                tab === 'LIVE' ? LIVE_INTERVAL_MS : REPORTED_INTERVAL_MS) : null;
        button.textContent = on ? button.dataset.labelOn : button.dataset.labelOff;
        button.setAttribute('aria-pressed', on ? 'true' : 'false');
    }

    button.addEventListener('click', function () {
        var next = !enabled();
        remember(next);
        apply(next);
    });

    apply(enabled());
})();
