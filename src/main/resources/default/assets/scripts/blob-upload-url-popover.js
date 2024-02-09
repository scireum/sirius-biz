/**
 * Inits the url upload popover.
 *
 * Uses jQuery to init bootstraps popover.
 *
 * @param _triggerElement The element (e.g. Button) that triggers the popover to enter an URL
 * @param _uploadContainer The container the _triggerElement and other components belongs to
 * @param _resetButton The button that resets the upload field
 * @param _fileNameContainer The container that shows the filename
 * @param i18n Contains information about the label, the ok-Button and the cancel-Button of the popover
 */
function initUrlUploadPopover(_triggerElement, _uploadContainer, _resetButton, _fileNameContainer, i18n, updateCallback) {
    const body = buildBody(_triggerElement, _uploadContainer, _resetButton);
    const container = buildContainer(_triggerElement.parentElement);

    if (!_triggerElement.popover) {
        _triggerElement.popover = new bootstrap.Tooltip(_triggerElement, {
            html: true,
            trigger: 'manual',
            content: body._container,
            container: container,
            placement: 'top'
        });
    }

    _triggerElement.addEventListener('click', () => {
        _triggerElement.popover.toggle();
    });

    _resetButton.addEventListener('click', function() {
        body._inputField.value = '';
    });

    function buildContainer() {
        const _container = document.createElement('div');
        _container.classList.add('popover-content-max');
        _triggerElement.parentElement.appendChild(_container);
        return _container;
    }

    function buildBody() {
        const body = {};
        buildInputFormGroupCol(body);
        buildOkButtonCol(body);
        buildCancelButtonCol(body);
        buildErrorCol(body);
        buildBodyContainer(body);
        return body;
    }

    function buildInputFormGroupCol(body) {
        body._inputLabel = document.createElement('label');
        body._inputLabel.innerHTML = i18n.label;

        body._inputField = document.createElement('input');
        body._inputField.classList.add('form-control');
        body._inputField.setAttribute('type', 'text');
        body._inputField.setAttribute('placeholder', 'https://');

        body._formGroup = document.createElement('div');
        body._formGroup.classList.add('form-group');
        body._formGroup.classList.add('mb-3');
        body._formGroup.append(body._inputLabel, body._inputField);

        body._inputCol = document.createElement('div');
        body._inputCol.classList.add('col', 'col-12');
        body._inputCol.appendChild(body._formGroup);
    }

    function buildOkButtonCol(body) {
        body._buttonOk = document.createElement('button');
        body._buttonOk.classList.add('btn', 'btn-block', 'btn-primary', 'btn-apply');
        body._buttonOk.innerHTML = i18n.ok + ' <i class="fa-solid fa-check"></i>';
        body._buttonOk.addEventListener('click', function (event) {
            event.preventDefault();
            updateURL(body._inputField.value);
        });

        body._leftCol = document.createElement('div');
        body._leftCol.classList.add('col', 'col-12', 'col-md-6');
        body._leftCol.appendChild(body._buttonOk);
    }

    function buildCancelButtonCol(body) {
        body._buttonCancel = document.createElement('div');
        body._buttonCancel.classList.add('btn', 'btn-block', 'btn-close', 'btn-outline-secondary');
        body._buttonCancel.innerHTML = i18n.cancel + ' <i class="fa-solid fa-close"></i>';
        body._buttonCancel.addEventListener('click', function () {
            _triggerElement.popover.toggle();
        });
        body._rightCol = document.createElement('div');
        body._rightCol.classList.add('col', 'col-12', 'col-md-6');
        body._rightCol.appendChild(body._buttonCancel);
    }

    function buildErrorCol(body) {
        body._errorCol = document.createElement('div');
        body._errorCol.classList.add('col', 'col-12');
    }

    function buildBodyContainer(body) {
        body._container = document.createElement('div');
        body._container.classList.add('row');
        body._container.append(body._inputCol, body._errorCol, body._leftCol, body._rightCol);
    }

    /**
     * Updates the image in the upload box and the url in the form.
     *
     * @param url the new URL
     */
    function updateURL(url) {
        if (url.startsWith('http://') || url.startsWith('https://')) {
            _uploadContainer.querySelector('input[name]').value = url;
            _resetButton.classList.remove('d-none');
            body._errorCol.innerHTML = '';
            _fileNameContainer.innerHTML = '<a href="' + url + '">' + fetchFileName(url) + ' <i class="fa-regular fa-arrow-up-right-from-square"></i></a>';
            _triggerElement.popover.hide();
            if (typeof updateCallback === 'function') {
                updateCallback(url);
            }
        } else {
            addErrorMessage(i18n.errorMsg);
        }
    }

    /**
     * Fetches the name of the file from the URL.
     *
     * @param path to fetch the name from
     * @returns {string|*} filename
     */
    function fetchFileName(path) {
        if (path === 'undefined' || path === '') {
            return '';
        }
        let pathParts = path.split("/");
        if (pathParts.length === 1) {
            return path;
        }
        return pathParts[pathParts.length - 1];
    }

    /**
     * Adds an error message next to the input field.
     * </p>
     * Only one message can be shown at the same time.
     *
     * @param message the message that is shown next to the input field
     */
    function addErrorMessage(message) {
        let _msg = document.createElement('div');
        _msg.innerHTML = '<div class="card full-border border-sirius-red-dark mb-3"><div class="card-body p-2 msgContent"></div></div>';
        _msg.querySelector('.msgContent').textContent = message;
        body._errorCol.innerHTML = '';
        body._errorCol.appendChild(_msg);
        _triggerElement.popover.update();
    }
}
