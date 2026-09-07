(function () {
    var video = document.getElementById('video-player');
    var errorMsg = document.getElementById('video-error');
    if (!video || !errorMsg) {
        return;
    }

    // Most likely cause here: the stream ticket expired mid-session (30-minute
    // TTL) and the browser re-requested a byte range against a now-dead URL.
    video.addEventListener('error', function () {
        errorMsg.hidden = false;
    });
})();
