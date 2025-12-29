/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

(function () {
    function extendUrlWithPageSize(currentLocation, value) {
        const key = "page-size";
        const url = new URL(currentLocation);
        url.searchParams.delete(key);
        url.searchParams.append(key, value);
        return url.toString();
    }

    sirius.ready(function () {
        document.querySelectorAll("a.page-size-link-js").forEach(function (_link) {
            _link.classList.forEach(function (_className) {
                if (_className.startsWith("page-size-link-")) {
                    _link.href = extendUrlWithPageSize(window.location.href, _className.replace("page-size-link-", ""));
                }
            });
        });
    });
})();

