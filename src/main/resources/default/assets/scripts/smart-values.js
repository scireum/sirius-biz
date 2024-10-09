function hideAllSmartValues(_excludedElement) {
    document.querySelectorAll('.smart-values-link-js').forEach(_element => {
        if (_element !== _excludedElement && _element.tooltip) {
            _element.tooltip.hide();
        }
    });
}

function openSmartValues(elementId, type, payload, signature, entityId, editorLabel) {
    const _element = document.querySelector('#' + elementId);
    hideAllSmartValues(_element);

    if (!_element.tooltip) {
        _element.tooltip = new bootstrap.Tooltip(_element, {
            animation: false,
            html: true,
            sanitize: false,
            trigger: 'manual',
            template: '<div class="tooltip smart-values" role="tooltip"><div class="tooltip-arrow"></div><div class="tooltip-inner"></div></div>',
            title: '<i class="fa-solid fa-sync fa-spin"></i>',
            delay: {show: 0, hide: 0},
        });
    }

    _element.tooltip.toggle();

    sirius.getJSON("/tycho/smartValues", {
        type: type,
        payload: payload,
        securityHash: signature
    }).then(json => {
        if (json.values.length === 0) {
            if (entityId === "") {
                _element.tooltip.hide();
                _element.classList.add('text-decoration-none');
                _element.classList.remove('link');
                _element.href = '';
                return;
            } else {
                json.values = {icon: 'fa-solid fa-pen-to-square', label: editorLabel, action: '/'+type+'/' + entityId};
            }
        }

        const html = Mustache.render('{{#values}}' +
            '<div class="d-flex flex-row align-items-center">' +
            '   <a href="{{action}}" class="smart-value-link btn btn-link d-flex flex-row align-items-center overflow-hidden flex-grow-1">' +
            '       <i class="{{icon}}"></i>' +
            '       <span class="ps-2">{{label}}</span>' +
            '   </a>' +
            '   {{#copyPayload}}' +
            '       <a href="javascript:sirius.copyToClipboard(\'{{copyPayload}}\')" class="smart-value-link btn btn-link ms-2 text-small">' +
            '          <i class="fa-regular fa-clipboard"></i>' +
            '       </a>' +
            '   {{/copyPayload}}' +
            '</div>' +
            '{{/values}}', json);

        _element.tooltip.setContent({'.tooltip-inner': html});
    });
}

sirius.ready(() => {
    document.addEventListener('click', event => {
        if (!event.target.classList.contains('smart-values-link-js')) {
            hideAllSmartValues();
        }
    });
    document.addEventListener('keyup', event => {
        if (event.key === sirius.key.ESCAPE) {
            hideAllSmartValues();
        }
    });
});
