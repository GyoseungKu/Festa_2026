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
    let timer = null;
    let submitted = false;
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { willReadFrequently: true });

    const setMessage = (text) => { message.textContent = text || ""; };

    const stopCamera = () => {
        if (timer) window.clearTimeout(timer);
        timer = null;
        if (stream) stream.getTracks().forEach((track) => track.stop());
        stream = null;
        video.srcObject = null;
        frame.classList.remove("camera-active");
        status.classList.remove("active");
        status.textContent = "대기 중";
        startButton.disabled = false;
        stopButton.disabled = true;
    };

    const scanFrame = async () => {
        if (!stream || submitted) return;
        try {
            let value = "";
            if (video.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA && video.videoWidth > 0) {
                const maxWidth = 960;
                const scale = Math.min(1, maxWidth / video.videoWidth);
                canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
                canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
                context.drawImage(video, 0, 0, canvas.width, canvas.height);
                const image = context.getImageData(0, 0, canvas.width, canvas.height);
                value = window.jsQR(image.data, image.width, image.height, {
                    inversionAttempts: "attemptBoth"
                })?.data?.trim() || "";
            }
            if (value) {
                submitted = true;
                tokenInput.value = value;
                status.textContent = "인식 완료";
                setMessage("QR을 인식했습니다. 사용자 정보를 조회합니다.");
                stopCamera();
                form.requestSubmit();
                return;
            }
        } catch (error) {
            setMessage(manualEntryAvailable
                ? "화면을 분석하는 중 문제가 발생했습니다. 다시 시도하거나 토큰을 직접 입력해 주세요."
                : "화면을 분석하는 중 문제가 발생했습니다. 다시 시도해 주세요.");
        }
        timer = window.setTimeout(scanFrame, 180);
    };

    const startCamera = async () => {
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
        try {
            stream = await navigator.mediaDevices.getUserMedia({
                video: { facingMode: { ideal: "environment" }, width: { ideal: 1280 }, height: { ideal: 720 } },
                audio: false
            });
            video.srcObject = stream;
            await video.play();
            frame.classList.add("camera-active");
            status.classList.add("active");
            status.textContent = "스캔 중";
            startButton.disabled = true;
            stopButton.disabled = false;
            scanFrame();
        } catch (error) {
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
