// 대나무숲 운영 화면의 자동 새로고침.
// 서버로 스트림을 여는 대신 10초마다 페이지를 다시 불러온다. 관리자는 몇 명뿐이라
// 이 정도 요청은 부담이 되지 않고, 화면 코드가 채팅 전송 방식과 얽히지 않는다.
(function () {
    var INTERVAL_MS = 10000;
    var STORAGE_KEY = 'bambooAutoRefresh';
    var button = document.getElementById('bamboo-auto-refresh');
    if (!button) return;

    var timer = null;

    function enabled() {
        try {
            return sessionStorage.getItem(STORAGE_KEY) === 'on';
        } catch (unavailable) {
            return false;
        }
    }

    function remember(value) {
        try {
            sessionStorage.setItem(STORAGE_KEY, value ? 'on' : 'off');
        } catch (unavailable) {
            // 저장소를 못 쓰면 이번 페이지에서만 동작한다.
        }
    }

    // 입력 중이거나 탭이 숨어 있으면 넘긴다. 관리자가 닉네임을 타이핑하는 중에
    // 새로고침이 걸리면 입력이 날아간다.
    function busy() {
        var active = document.activeElement;
        if (document.visibilityState !== 'visible') return true;
        if (!active) return false;
        var tag = active.tagName;
        return tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA';
    }

    function tick() {
        if (busy()) return;
        window.location.reload();
    }

    function apply(on) {
        if (timer !== null) {
            window.clearInterval(timer);
            timer = null;
        }
        if (on) timer = window.setInterval(tick, INTERVAL_MS);
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
