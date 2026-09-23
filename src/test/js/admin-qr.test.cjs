const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

const source = readFileSync(resolve(__dirname, '../../main/resources/static/js/admin-qr.js'), 'utf8');
const flush = async () => { for (let i = 0; i < 10; i++) await Promise.resolve(); };
const deferred = () => {
    let resolve;
    const promise = new Promise((done) => { resolve = done; });
    return { promise, resolve };
};

function setup({ native, videoCallbacks = true, getUserMedia } = {}) {
    const elements = new Map();
    const callbacks = new Map();
    let nextId = 0;
    let submissions = 0;
    let decodes = 0;
    let stopped = 0;
    let decoded = '';
    const element = (id) => {
        if (!elements.has(id)) elements.set(id, {
            disabled: false, type: 'hidden', textContent: '',
            classList: { add() {}, remove() {} },
            handlers: {}, addEventListener(name, fn) { this.handlers[name] = fn; }
        });
        return elements.get(id);
    };
    const schedule = (fn) => { callbacks.set(++nextId, fn); return nextId; };
    const cancel = (id) => callbacks.delete(id);
    const video = element('qr-video');
    Object.assign(video, {
        readyState: 2, videoWidth: 1280, videoHeight: 720, currentTime: 0,
        play: async () => {},
        ...(videoCallbacks ? { requestVideoFrameCallback: schedule, cancelVideoFrameCallback: cancel } : {})
    });
    element('qr-scan-form').requestSubmit = () => { submissions++; };
    const media = { getTracks: () => [{ stop: () => { stopped++; } }] };
    const window = {
        requestAnimationFrame: schedule, cancelAnimationFrame: cancel,
        addEventListener() {},
        jsQR: () => { decodes++; return decoded ? { data: decoded } : null; },
        ...(native ? { BarcodeDetector: class {
            static async getSupportedFormats() { return ['qr_code']; }
            detect() { return native(); }
        } } : {})
    };
    vm.runInNewContext(source, {
        window, document: {
            getElementById: element,
            createElement: () => ({ getContext: () => ({
                drawImage() {}, getImageData: () => ({ data: new Uint8ClampedArray(4) })
            }) })
        },
        navigator: { mediaDevices: { getUserMedia: getUserMedia || (async () => media) } },
        HTMLMediaElement: { HAVE_CURRENT_DATA: 2 }
    });
    return {
        element, video, callbacks,
        start: () => element('camera-start').handlers.click(),
        stop: () => element('camera-stop').handlers.click(),
        result: () => ({ submissions, decodes, stopped }),
        decode: (value) => { decoded = value; },
        async nextFrame() {
            const [id, callback] = callbacks.entries().next().value;
            callbacks.delete(id);
            callback();
            await flush();
        }
    };
}

test('submits a QR on the next video frame and cancels scanning', async () => {
    const app = setup();
    await app.start();
    assert.equal(app.result().decodes, 1);
    app.decode(' token ');
    app.video.currentTime += 1 / 30;
    await app.nextFrame();
    assert.deepEqual(app.result(), { submissions: 1, decodes: 2, stopped: 1 });
    assert.equal(app.element('token').value, 'token');
    assert.equal(app.element('camera-status').textContent, '인식 완료');
    assert.equal(app.callbacks.size, 0);
});

test('animation fallback skips unchanged frames and stops cleanly', async () => {
    const app = setup({ videoCallbacks: false });
    await app.start();
    await app.nextFrame();
    assert.equal(app.result().decodes, 1);
    app.stop();
    assert.equal(app.callbacks.size, 0);
    assert.equal(app.result().stopped, 1);
});

test('native QR result submits immediately without jsQR decoding', async () => {
    const app = setup({ native: async () => [{ rawValue: 'native-token' }] });
    await flush();
    await app.start();
    await flush();
    assert.equal(app.result().submissions, 1);
    assert.equal(app.result().decodes, 0);
});

test('native failure falls back to jsQR in the same frame', async () => {
    const app = setup({ native: async () => { throw new Error('unsupported'); } });
    await flush();
    app.decode('fallback-token');
    await app.start();
    await flush();
    assert.equal(app.result().submissions, 1);
    assert.equal(app.element('token').value, 'fallback-token');
});

test('late detection from a stopped session cannot submit or disturb a restart', async () => {
    const pending = deferred();
    const app = setup({ native: () => pending.promise });
    await flush();
    await app.start();
    app.stop();
    app.video.readyState = 0;
    await app.start();
    pending.resolve([{ rawValue: 'stale-token' }]);
    await flush();
    assert.equal(app.result().submissions, 0);
    assert.equal(app.callbacks.size, 1);
    app.stop();
});

test('double start is ignored and a camera granted after stop is released', async () => {
    const pending = deferred();
    let requests = 0;
    let stops = 0;
    const app = setup({ getUserMedia: () => { requests++; return pending.promise; } });
    const opening = app.start();
    await app.start();
    app.stop();
    pending.resolve({ getTracks: () => [{ stop: () => { stops++; } }] });
    await opening;
    assert.equal(requests, 1);
    assert.equal(stops, 1);
    assert.equal(app.video.srcObject, null);
    assert.equal(app.callbacks.size, 0);
});
