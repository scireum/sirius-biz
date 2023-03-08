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
    $(triggerElement).popover({
        html: true,
        trigger: 'manual',
        content: buildBody(triggerElement, uploadContainer, resetButton),
        container: buildContainer(triggerElement.parentElement),
        placement: 'top'
    });

    triggerElement.addEventListener("click", function() {
        $(triggerElement).popover('toggle');
    });

    function buildContainer() {
        const container = document.createElement("div");
        container.classList.add("popover-content-max");
        triggerElement.parentElement.appendChild(container);
        return container;
    }

    function buildBody() {
        const container = document.createElement("div");
        container.classList.add("row");

        // Input field
        const inputCol = document.createElement("div");
        inputCol.classList.add("col", "col-12");

        const formGroup = document.createElement("div");
        formGroup.classList.add("form-group");

        const inputLabel = document.createElement("label");
        inputLabel.innerHTML = i18n.label;

        const inputField = document.createElement("input");
        inputField.classList.add("form-control");
        inputField.setAttribute("type", "text");

        formGroup.append(inputLabel, inputField);
        inputCol.appendChild(formGroup);

        // Button OK
        const leftCol = document.createElement("div");
        leftCol.classList.add("col", "col-12", "col-md-6");

        const buttonOk = document.createElement("button");
        buttonOk.classList.add("btn", "btn-block", "btn-primary", "btn-apply");
        buttonOk.innerHTML = i18n.ok + ' <i class="fa fa-check"></i>';
        buttonOk.addEventListener("click", function(event) {
            event.preventDefault();
            updateURL(inputField.value);
        })

        leftCol.appendChild(buttonOk);

        // Button cancel
        const rightCol = document.createElement("div");
        rightCol.classList.add("col" , "col-12", "col-md-6");

        const buttonCancel = document.createElement("div");
        buttonCancel.classList.add("btn", "btn-block", "btn-close", "btn-outline-secondary");
        buttonCancel.innerHTML = i18n.cancel + ' <i class="fa fa-close"></i>';
        buttonCancel.addEventListener("click", function() {
            $(triggerElement).popover('toggle');
        });

        resetButton.addEventListener("click", function() {
            inputField.value = "";
        });

        rightCol.appendChild(buttonCancel);

        container.append(inputCol, leftCol, rightCol);

        return container;
    }

    /**
     * Updates the image in the upload box and the url in the form.
     *
     * @param url the new URL
     */
    const updateURL = function (url) {
        if (url.startsWith('http://') || url.startsWith('https://')) {
            let img = uploadContainer.querySelector(".img-preview img");
            img.setAttribute("src", url);
            img.onerror = function() {
                img.setAttribute("src", "/assets/frontend/defaulticons/default-item.png");
            }
            uploadContainer.querySelector(".sirius-upload-content input[name]").value = url;
            resetButton.classList.remove("d-none");
            $(triggerElement).popover('hide');
        }
    };
}

