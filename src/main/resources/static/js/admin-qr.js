(() => {
    "use strict";

    const startButton = document.getElementById("camera-start");
    const stopButton = document.getElementById("camera-stop");
    const video = document.getElementById("qr-video");
    const frame = document.getElementById("camera-frame");
    const status = document.getElementById("camera-status");
    const message = document.getElementById("camera-message");
    const tokenInput = document.getElementById("token");
    const form = document.getElementById("qr-scan-form");
    const manualEntryAvailable = tokenInput?.type !== "hidden";

    if (!startButton || !video || !form) return;

    let stream = null;
    let frameCallback = null;
    let session = 0;
    let lastVideoTime = -1;
    let detector = null;
    let submitted = false;
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { willReadFrequently: true });

    const setMessage = (text) => { message.textContent = text || ""; };

    // Keep jsQR available for browsers without native QR support or failed detection.
    if (typeof window.BarcodeDetector?.getSupportedFormats === "function") {
        window.BarcodeDetector.getSupportedFormats().then((formats) => {
            if (formats.includes("qr_code")) {
                detector = new window.BarcodeDetector({ formats: ["qr_code"] });
            }
        }).catch(() => { detector = null; });
    }

    const stopCamera = () => {
        session += 1;
        if (frameCallback !== null) {
            if (typeof video.requestVideoFrameCallback === "function") {
                video.cancelVideoFrameCallback(frameCallback);
            } else {
                window.cancelAnimationFrame(frameCallback);
            }
        }
        frameCallback = null;
        if (stream) stream.getTracks().forEach((track) => track.stop());
        stream = null;
        video.srcObject = null;
        frame.classList.remove("camera-active");
        status.classList.remove("active");
        status.textContent = "대기 중";
        startButton.disabled = false;
        stopButton.disabled = true;
    };

    const scheduleFrame = (activeSession) => {
        if (!stream || submitted || activeSession !== session) return;
        const callback = () => {
            frameCallback = null;
            scanFrame(activeSession);
        };
        frameCallback = typeof video.requestVideoFrameCallback === "function"
            ? video.requestVideoFrameCallback(callback)
            : window.requestAnimationFrame(callback);
    };

    const decodeWithJsQr = () => {
        const scale = Math.min(1, 960 / video.videoWidth);
        const width = Math.max(1, Math.round(video.videoWidth * scale));
        const height = Math.max(1, Math.round(video.videoHeight * scale));
        if (canvas.width !== width) canvas.width = width;
        if (canvas.height !== height) canvas.height = height;
        context.drawImage(video, 0, 0, width, height);
        const image = context.getImageData(0, 0, width, height);
        return window.jsQR(image.data, width, height, {
            inversionAttempts: "attemptBoth"
        })?.data?.trim() || "";
    };

    const scanFrame = async (activeSession) => {
        if (!stream || submitted || activeSession !== session) return;
        try {
            let value = "";
            if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth > 0
                && video.videoHeight > 0 && video.currentTime !== lastVideoTime) {
                lastVideoTime = video.currentTime;
                if (detector) {
                    try {
                        const codes = await detector.detect(video);
                        value = codes.find((code) => code.rawValue?.trim())?.rawValue.trim() || "";
                    } catch (error) {
                        if (activeSession !== session) return;
                        detector = null;
                    }
                    if (activeSession !== session || !stream || submitted) return;
                }
                if (!value) value = decodeWithJsQr();
            }
            if (value) {
                submitted = true;
                tokenInput.value = value;
                setMessage("QR을 인식했습니다. 사용자 정보를 조회합니다.");
                stopCamera();
                status.textContent = "인식 완료";
                form.requestSubmit();
                return;
            }
        } catch (error) {
            setMessage(manualEntryAvailable
                ? "화면을 분석하는 중 문제가 발생했습니다. 다시 시도하거나 토큰을 직접 입력해 주세요."
                : "화면을 분석하는 중 문제가 발생했습니다. 다시 시도해 주세요.");
        }
        scheduleFrame(activeSession);
    };

    const startCamera = async () => {
        if (startButton.disabled || stream) return;
        submitted = false;
        setMessage("");
        if (typeof window.jsQR !== "function" || !context) {
            setMessage(manualEntryAvailable
                ? "QR 판독기를 불러오지 못했습니다. 페이지를 새로고침하거나 토큰을 직접 입력해 주세요."
                : "QR 판독기를 불러오지 못했습니다. 페이지를 새로고침해 주세요.");
            return;
        }
        if (!navigator.mediaDevices?.getUserMedia) {
            setMessage("카메라를 사용할 수 없습니다. HTTPS 연결인지 확인해 주세요.");
            return;
        }
        startButton.disabled = true;
        stopButton.disabled = false;
        const activeSession = ++session;
        lastVideoTime = -1;
        try {
            const cameraStream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
                audio: false
            });
            if (activeSession !== session) {
                cameraStream.getTracks().forEach((track) => track.stop());
                return;
            }
            stream = cameraStream;
            video.srcObject = stream;
            await video.play();
            if (activeSession !== session) return;
            frame.classList.add("camera-active");
            status.classList.add("active");
            status.textContent = "스캔 중";
            startButton.disabled = true;
            stopButton.disabled = false;
            scanFrame(activeSession);
        } catch (error) {
            if (activeSession !== session) return;
            stopCamera();
            setMessage("카메라 권한을 허용할 수 없거나 카메라를 찾지 못했습니다. 브라우저 설정을 확인해 주세요.");
        }
    };

    startButton.addEventListener("click", startCamera);
    stopButton.addEventListener("click", () => {
        stopCamera();
        setMessage("카메라를 중지했습니다.");
    });
    window.addEventListener("pagehide", stopCamera);
})();
