(() => {
    'use strict';
    const container = document.getElementById('booth-map');
    if (!container) return;
    const status = document.getElementById('booth-map-status');
    const notify = message => { status.textContent = message; status.hidden = false; };
    if (!window.L) {
        notify('지도를 불러오지 못했습니다. 인터넷 연결을 확인해 주세요. 좌표는 직접 입력할 수 있습니다.');
        return;
    }
    const campus = [37.6436, 127.1056];
    const map = L.map(container).setView(campus, 17);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).on('tileerror', () => notify('지도 배경을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.')).addTo(map);
    const coordinates = (latitude, longitude) => {
        if (latitude == null || longitude == null || String(latitude).trim() === '' || String(longitude).trim() === '') return null;
        const lat = Number(latitude), lng = Number(longitude);
        return Number.isFinite(lat) && Number.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
            ? [lat, lng] : null;
    };
    if (container.dataset.mode === 'list') {
        const points = [];
        document.querySelectorAll('[data-booth-marker]').forEach(row => {
            const point = coordinates(row.dataset.latitude, row.dataset.longitude);
            if (!point) return;
            points.push(point);
            const popup = document.createElement('div');
            const title = document.createElement('strong');
            title.textContent = row.dataset.name;
            const operator = document.createElement('div');
            operator.textContent = row.dataset.operator;
            const edit = row.querySelector('.booth-name-link').cloneNode(true);
            edit.textContent = '부스 수정';
            popup.append(title, operator, edit);
            L.marker(point).addTo(map).bindPopup(popup);
        });
        if (points.length) map.fitBounds(L.latLngBounds(points), { padding: [32, 32], maxZoom: 18 });
        return;
    }
    const latitude = document.getElementById('latitude');
    const longitude = document.getElementById('longitude');
    if (!latitude || !longitude) return;
    let marker;
    const place = point => {
        if (marker) marker.setLatLng(point);
        else {
            marker = L.marker(point, { draggable: true }).addTo(map);
            marker.on('dragend', () => select(marker.getLatLng()));
        }
    };
    const select = point => {
        latitude.value = point.lat.toFixed(7);
        longitude.value = point.lng.toFixed(7);
        latitude.dispatchEvent(new Event('input', { bubbles: true }));
        longitude.dispatchEvent(new Event('input', { bubbles: true }));
        place([point.lat, point.lng]);
    };
    const initial = coordinates(latitude.value, longitude.value);
    if (initial) { place(initial); map.setView(initial, 18); }
    map.on('click', event => select(event.latlng));
    const sync = () => {
        const point = coordinates(latitude.value, longitude.value);
        if (point) { place(point); map.panTo(point); }
        else if (marker) { map.removeLayer(marker); marker = null; }
    };
    latitude.addEventListener('change', sync);
    longitude.addEventListener('change', sync);
})();
