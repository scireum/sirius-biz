/**
 * Inits the url upload popover.
 *
 * Uses jQuery to init bootstraps popover.
 *
 * @param triggerElement The element (e.g. Button) that triggers the popover to enter an URL
 * @param uploadContainer The container the triggerElement and other components belongs to
 * @param resetButton The button that resets the upload field
 * @param i18n Contains information about the label, the ok-Button and the cancel-Button of the popover
 */
function initUrlUploadPopover(triggerElement, uploadContainer, resetButton, i18n) {
    const body = buildBody(triggerElement, uploadContainer, resetButton);
    const container = buildContainer(triggerElement.parentElement);
    $(triggerElement).popover({
        html: true,
        trigger: 'manual',
        content: body.container,
        container: container,
        placement: 'top'
    });

    triggerElement.addEventListener('click', function() {
        $(triggerElement).popover('toggle');
    });

    resetButton.addEventListener('click', function() {
        body.inputField.value = '';
    });

    function buildContainer() {
        const container = document.createElement('div');
        container.classList.add('popover-content-max');
        triggerElement.parentElement.appendChild(container);
        return container;
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
        body.inputLabel = document.createElement('label');
        body.inputLabel.innerHTML = i18n.label;

        body.inputField = document.createElement('input');
        body.inputField.classList.add('form-control');
        body.inputField.setAttribute('type', 'text');
        body.inputField.setAttribute('placeholder', 'https://');

        body.formGroup = document.createElement('div');
        body.formGroup.classList.add('form-group');
        body.formGroup.append(body.inputLabel, body.inputField);

        body.inputCol = document.createElement('div');
        body.inputCol.classList.add('col', 'col-12');
        body.inputCol.appendChild(body.formGroup);
    }

    function buildOkButtonCol(body) {
        body.buttonOk = document.createElement('button');
        body.buttonOk.classList.add('btn', 'btn-block', 'btn-primary', 'btn-apply');
        body.buttonOk.innerHTML = i18n.ok + ' <i class="fas fa-check"></i>';
        body.buttonOk.addEventListener('click', function (event) {
            event.preventDefault();
            updateURL(body.inputField.value);
        });

        body.leftCol = document.createElement('div');
        body.leftCol.classList.add('col', 'col-12', 'col-md-6');
        body.leftCol.appendChild(body.buttonOk);
    }

    function buildCancelButtonCol(body) {
        body.buttonCancel = document.createElement('div');
        body.buttonCancel.classList.add('btn', 'btn-block', 'btn-close', 'btn-outline-secondary');
        body.buttonCancel.innerHTML = i18n.cancel + ' <i class="fas fa-close"></i>';
        body.buttonCancel.addEventListener('click', function () {
            $(triggerElement).popover('toggle');
        });
        body.rightCol = document.createElement('div');
        body.rightCol.classList.add('col', 'col-12', 'col-md-6');
        body.rightCol.appendChild(body.buttonCancel);
    }

    function buildErrorCol(body) {
        body.errorCol = document.createElement('div');
        body.errorCol.classList.add('col', 'col-12');
    }

    function buildBodyContainer(body) {
        body.container = document.createElement('div');
        body.container.classList.add('row');
        body.container.append(body.inputCol, body.errorCol, body.leftCol, body.rightCol);
    }

    /**
     * Updates the image in the upload box and the url in the form.
     *
     * @param url the new URL
     */
    const updateURL = function (url) {
        if (url.startsWith('http://') || url.startsWith('https://')) {
            let img = uploadContainer.querySelector('.img-preview img');
            img.setAttribute('src', url);
            img.onerror = function() {
                img.setAttribute('src', '/assets/frontend/defaulticons/default-item.png');
            }
            uploadContainer.querySelector('input[name]').value = url;
            resetButton.classList.remove('d-none');
            body.errorCol.innerHTML = '';
            $(triggerElement).popover('hide');
        } else {
            addErrorMessage(i18n.errorMsg);
        }
    };

    /**
     * Adds an error message next to the input field.
     * </p>
     * Only one message can be shown at the same time.
     *
     * @param message the message that is shown next to the input field
     */
    function addErrorMessage(message) {
        let msg = document.createElement('div');
        msg.innerHTML = '<div class="card full-border border-sirius-red-dark mb-3"><div class="card-body p-2 msgContent"></div></div>';
        msg.querySelector('.msgContent').textContent = message;
        body.errorCol.innerHTML = '';
        body.errorCol.appendChild(msg);
        $(triggerElement).popover('update');
    }
}

