function openSmartValues(elementId, type, payload, signature) {
    $('#' + elementId).tooltip({
        html: true,
        sanitize: false,
        trigger: 'manual',
        template: '<div class="tooltip smart-values" role="tooltip"><div class="arrow"></div><div class="tooltip-inner"></div></div>',
        title: '<i class="fa-solid fa-sync fa-spin"></i>',
        delay: {start: 0, hide: 1000}
    }).tooltip('show');

    $(document).on('click.smart-values', function (event) {
        $('#' + elementId).tooltip('hide');
        $(document).off('click.smart-values');
        $(document).off('keyup.smart-values');
    });
    $(document).on('keyup.smart-values', function (event) {
        if (event.key === sirius.key.ESCAPE) {
            $('#' + elementId).tooltip('hide');
            $(document).off('click.smart-values');
            $(document).off('keyup.smart-values');
        }
    });

    sirius.getJSON("/tycho/smartValues", {
        type: type,
        payload: payload,
        securityHash: signature
    }).then(function (json) {
        if (json.values.length === 0) {
            $('#' + elementId).tooltip('hide').removeClass('link').attr('href', '');
            $(document).off('click.smart-values');
            $(document).off('keyup.smart-values');
            return;
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
            '   {{^copyPayload}}' +
            '       <a class="btn btn-link disabled ms-2 text-small">' +
            '          <i class="fa-regular fa-clipboard"></i>' +
            '       </a>' +
            '   {{/copyPayload}}' +
            '</div>' +
            '{{/values}}', json);

        $('#' + elementId).attr('data-original-title', html).tooltip('show');
    });
}
