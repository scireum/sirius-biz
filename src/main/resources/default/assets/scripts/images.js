// Used by blobImage to safely load a blob variant image. This will show a spinner while waiting for the conversion and
// also handle errors gracefully.
function loadImageLazily(_img) {
    const mainUrl = _img.dataset.src;
    const fallback = _img.getAttribute('src');
    const _container = _img.parentNode;
    let waitTime = 500;

    function attemptLoad(remainingAttempts) {
        if (remainingAttempts <= 0) {
            _container.innerHTML = '';
            _img.src = fallback;
            _container.appendChild(_img);
            return;
        }

        const image = new Image();
        image.src = mainUrl;
        image.setAttribute('style', _img.getAttribute('style'));
        image.setAttribute('class', _img.getAttribute('class'));
        image.onload = function () {
            _container.innerHTML = '';
            _container.appendChild(image);
        };

        image.onerror = function () {
            setTimeout(function () {
                attemptLoad(remainingAttempts - 1);
            }, waitTime);
            waitTime += 500;
        };
    }

    _container.innerHTML = '<i class="fa fa-sync-alt fa-spin"></i>';
    attemptLoad(3);
}

function loadImageSafely(_img) {
    _img.onerror = function () {
        _img.dataset.src = _img.getAttribute('src');
        _img.setAttribute('src', _img.dataset.fallback);
        sirius.loadImageLazily(_img);
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


