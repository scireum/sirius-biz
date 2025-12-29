// Used by blobImage to safely load a blob variant image. This will show a spinner while waiting for the conversion and
// also handle errors gracefully.
function loadImageLazily(_img) {
    const mainUrl = _img.dataset.src;
    const fallback = _img.getAttribute('src');
    const failed = _img.dataset.failed;
    const _container = _img.parentNode;
    let waitTime = 500;

    function attemptLoad(remainingAttempts) {
        if (remainingAttempts <= 0) {
            _container.innerHTML = '';
            _img.src = fallback;
            _container.appendChild(_img);
            return;
        }

        fetch(mainUrl).then(function (response) {
            if (response.ok) {
                return response.blob()
            } else if (response.status === 404 || response.status === 500) {
                _container.innerHTML = '';
                _img.src = failed;
                _container.appendChild(_img);
            } else if (response.status === 503) {
                setTimeout(function () {
                    attemptLoad(remainingAttempts - 1);
                }, waitTime);
                waitTime += 500;
            }
            return null;
        }).then(function (imageBlob) {
            if (imageBlob) {
                _container.innerHTML = '';
                _img.src = URL.createObjectURL(imageBlob);
                _container.appendChild(_img);
            }
        });
    }

    _container.innerHTML = '<i class="fa-solid fa-sync-alt fa-spin"></i>';
    attemptLoad(3);
}

function loadImageSafely(_img) {
    _img.onerror = function () {
        _img.dataset.src = _img.getAttribute('src');
        _img.setAttribute('src', _img.dataset.fallback);
        loadImageLazily(_img);
    };
}

sirius.ready(function () {
    document.querySelectorAll('.lazy-image-js').forEach(function (_node) {
        loadImageLazily(_node);
    });
    document.querySelectorAll('.safe-image-js').forEach(function (_node) {
        loadImageSafely(_node);
    });
});
