(function() {
    function extendUrlWithDisplayMode(currentLocation, value) {
        const key = "display-mode";
        const url = new URL(currentLocation);
        url.searchParams.delete(key);
        url.searchParams.append(key, value);
        return url.toString();
    }

    sirius.ready(function() {
        document.querySelectorAll("a.list-link-cards-js").forEach(function (_link) {
            _link.href = extendUrlWithDisplayMode(window.location.href, "CARDS");
        });
        document.querySelectorAll("a.list-link-list-js").forEach(function (_link) {
            _link.href = extendUrlWithDisplayMode(window.location.href, "LIST");
        });
    });
})();

