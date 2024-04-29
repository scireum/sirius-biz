/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

/**
 * Initializes the given blobSoftRefField
 *
 * @param element          the field element
 * @param blobKeyField     the input field to store the blob key in
 * @param blobStorageSpace the storage space of the referenced blob
 * @param originalUrl      the url of the currently referenced blob
 * @param originalPath     the path of the currently referenced blob
 * @param defaultPreview   the default preview image to display if no file is referenced
 */
function initBlobSoftRefField(element, blobKeyField, blobStorageSpace, originalUrl, originalPath, defaultPreview) {
    const fileTemplate =
        '{{#previewImage}}' +
        '    <img src="{{previewImage}}" alt=""/>' +
        '{{/previewImage}}' +
        '{{^previewImage}}' +
        '    {{#icon}}' +
        '        <i class="fa-solid fa-3x {{icon}}" aria-hidden="true"></i>' +
        '    {{/icon}}' +
        '{{/previewImage}}' +
        '{{#url}}' +
        '    <p>' +
        '        <a href="{{url}}">' +
        '            {{filename}}' +
        '        </a>' +
        '    </p>' +
        '{{/url}}';

    const blobStorageSpacePath = '/' + blobStorageSpace;

    const selectButton = element.querySelector('.btn-select-file-js');
    const urlButton = element.querySelector('[data-toggle=popover]');
    const resetButton = element.querySelector('.btn-reset-js');
    const fileElement = element.querySelector('.file-js');

    $(urlButton).popover({
        html: true, trigger: 'manual', content: function () {
            return element.querySelector('.popover-content').innerHTML;
        }
    });

    $(urlButton).on('inserted.bs.popover', function () {
        const popover = urlButton.parentElement.querySelector('.popover');
        const closeButton = popover.querySelector('.button-close');
        const applyButton = popover.querySelector('.button-apply');
        const input = popover.querySelector('input');

        closeButton.addEventListener('click', function (event) {
            event.preventDefault();
            $(urlButton).popover('hide');
        });

        input.addEventListener('input', function () {
            checkURL(input.value, applyButton);
        });

        input.addEventListener('paste', function () {
            checkURL(input.value, applyButton);
        });

        input.addEventListener('keyup', function (event) {
            if (event.key === sirius.key.ENTER) {
                updateURL(input.value);
            }
        });

        applyButton.addEventListener('click', function (event) {
            event.preventDefault();
            updateURL(input.value);
        });

        let url = blobKeyField.value;

        if (!url.startsWith('http://') && !url.startsWith('https://')) {
            url = '';
        }

        input.value = url;
        input.select();

        checkURL(input.value, applyButton);
    });

    urlButton.addEventListener('click', function (event) {
        event.preventDefault();
        urlButton.blur();

        $(urlButton).popover('toggle');
    });

    resetButton.addEventListener('click', function (event) {
        event.preventDefault();
        resetButton.blur();

        blobKeyField.value = '';

        updateFile('');
        updateResetButton();
    });

    selectButton.addEventListener('click', function (event) {
        event.preventDefault();

        const currentPath = element.dataset.path || blobStorageSpacePath;

        selectVFSFile(currentPath, blobStorageSpacePath, '').then(function (selectedValue) {
            $.getJSON('/dasd/blob-info-for-path/' + blobStorageSpace, {
                path: selectedValue.substring(blobStorageSpacePath.length)
            }, function (json) {
                blobKeyField.value = json.fileId;
                element.dataset.path = determineParentPath(selectedValue);

                updateFile(json.downloadUrl, json.filename);
                updateResetButton();
            });
        });
    });

    const updateResetButton = function () {
        if (blobKeyField.value === '') {
            resetButton.classList.add('hide');
        } else {
            resetButton.classList.remove('hide');
        }
    };

    /**
     * Updates the image in the upload box and the url in the form.
     *
     * @param url the new URL
     */
    const updateURL = function (url) {
        if (url.startsWith('http://') || url.startsWith('https://')) {
            blobKeyField.value = url;

            updateFile(url);
            updateResetButton();

            $(urlButton).popover('hide');
        }
    };

    /**
     * Checks if the specified URL is a valid one and disables or enables the apply button accordingly.
     *
     * @param url          the new URL
     * @param applyButton the button to enable/disable
     */
    const checkURL = function (url, applyButton) {
        if ('undefined' === typeof url || !(url.startsWith('http://') || url.startsWith('https://'))) {
            applyButton.disabled = true;
            return;
        }

        applyButton.disabled = false;
    };

    const updateFile = function (url, filename) {
        if (url.length === 0) {
            fileElement.innerHTML = Mustache.render(fileTemplate, {
                previewImage: defaultPreview,
                icon: 'fa-file-o'
            });

            return;
        }

        filename = filename || determineFilename(url);

        if (url.startsWith('http://') || url.startsWith('https://')) {
            fileElement.innerHTML = Mustache.render(fileTemplate, {
                url: url,
                filename: filename,
                icon: 'fa-external-link'
            });

            return;
        }

        const extension = url.substr(url.lastIndexOf(".") + 1).toLowerCase();

        if (extension === "jpg" || extension === "jpeg" || extension === "png") {
            fileElement.innerHTML = Mustache.render(fileTemplate, {
                url: url,
                filename: filename,
                previewImage: url
            });

            return;
        }

        fileElement.innerHTML = Mustache.render(fileTemplate, {
            url: url,
            filename: filename,
            icon: determineIcon(extension)
        });
    };

    const determineIcon = function (extension) {
        if (extension === "pdf") {
            return 'fa-file-pdf-o';
        }

        if (extension === "tif" || extension === "tiff" || extension === "bmp" || extension === "svg") {
            return 'fa-file-image-o';
        }

        if (extension === "doc" || extension === "docx") {
            return 'fa-file-word-o';
        }

        if (extension === "xls" || extension === "xlsx") {
            return 'fa-file-excel-o';
        }

        if (extension === "ppt" || extension === "pps" || extension === "pptx" || extension === "ppsx") {
            return 'fa-file-powerpoint-o';
        }

        if (extension === "zip" || extension === "rar") {
            return 'fa-file-archive-o';
        }

        return 'fa-file-o';
    };

    const determineFilename = function (path) {
        return path.substr(path.lastIndexOf("/") + 1);
    };

    const determineParentPath = function (path) {
        return path.substr(0, path.lastIndexOf("/"));
    };

    element.dataset.path = determineParentPath(originalPath);
    updateFile(originalUrl, determineFilename(originalPath));
}
