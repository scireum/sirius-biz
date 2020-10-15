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
 * @@param $element         the field element
 * @@param $blobKeyField    the input field to store the blob key in
 * @@param blobStorageSpace the storage space of the referenced blob
 * @@param originalUrl      the url of the currently referenced blob
 * @@param originalFilename the filename of the currently referenced blob
 * @@param originalPath     the path of the currently referenced blob
 * @@param defaultPreview   the default preview image to display if no file is referenced
 */
function initBlobSoftRefField($element,
                              $blobKeyField,
                              blobStorageSpace,
                              originalUrl,
                              originalFilename,
                              originalPath,
                              defaultPreview) {
    const fileTemplate =
        '{{#previewImage}}' +
        '    <img src="{{previewImage}}" alt=""/>' +
        '{{/previewImage}}' +
        '{{^previewImage}}' +
        '    {{#icon}}' +
        '        <i class="fa fa-3x {{icon}}" aria-hidden="true"></i>' +
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

    const $selectButton = $element.find('.btn-select-file-js');
    const $urlButton = $element.find('[data-toggle=popover]');
    const $resetButton = $element.find('.btn-reset-js');
    const $fileElement = $element.find('.file-js');

    $element.data('path', originalPath);

    $urlButton.popover({
        html: true, trigger: 'manual', content: function () {
            return $element.find('.popover-content').html();
        }
    });

    $urlButton.on('inserted.bs.popover', function () {
        const $popup = $(this);
        const $closeButton = $popup.next('.popover').find('.button-close');
        const $applyButton = $popup.next('.popover').find('.button-apply');
        const $input = $popup.next('.popover').find('input');

        $closeButton.click(function () {
            $popup.popover('hide');
        });

        $input.bind("input propertychange", function () {
            checkURL($input.val(), $applyButton);
        });

        $input.keyup(function (e) {
            if (e.which === 13) {
                updateURL($input.val());
            }
        });

        $applyButton.click(function () {
            updateURL($input.val());
        });
    });

    $urlButton.click(function () {
        let url = $blobKeyField.val();

        if (!url.startsWith('http://') && !url.startsWith('https://')) {
            url = '';
        }

        $(this).blur();
        $(this).popover('toggle');

        const $input = $(this).next('.popover').find('input');
        const $applyButton = $(this).next('.popover').find('.button-apply');

        $input.val(url).select();

        checkURL($input.val(), $applyButton);
    });

    $resetButton.click(function (e) {
        e.preventDefault();
        $(this).blur();

        $blobKeyField.val('');

        updateFile('');
        updateResetButton();
    });

    $selectButton.click(function () {
        const currentPath = $element.data('path') || blobStorageSpacePath;

        selectVFSFile(currentPath, blobStorageSpacePath).then(function (selectedValue) {
            $.getJSON('/dasd/blob-info-for-path/' + blobStorageSpace, {
                path: selectedValue.substring(blobStorageSpacePath.length)
            }, function (json) {
                $blobKeyField.val(json.fileId);
                $element.data('path', selectedValue);

                updateFile(json.downloadUrl, json.filename);
                updateResetButton();
            });
        });
    });

    const updateResetButton = function () {
        if ($blobKeyField.val() === '') {
            $resetButton.addClass("hide");
        } else {
            $resetButton.removeClass("hide");
        }
    };

    /**
     * Updates the image in the upload box and the url in the form.
     *
     * @@param url the new URL
     */
    const updateURL = function (url) {
        if (url.startsWith('http://') || url.startsWith('https://')) {
            $blobKeyField.val(url);

            updateFile(url);
            updateResetButton();

            $element.find('[data-toggle=popover]').popover('hide');
        }
    };

    /**
     * Checks if the specified URL is a valid one and disables or enables the apply button accordingly.
     *
     * @@param url          the new URL
     * @@param $applyButton the button to enable/disable
     */
    const checkURL = function (url, $applyButton) {
        if ('undefined' === typeof url || !(url.startsWith('http://') || url.startsWith('https://'))) {
            $applyButton.prop('disabled', true);
            return;
        }

        $applyButton.prop('disabled', false);
    };

    const updateFile = function (url, filename) {
        if (url.length === 0) {
            $fileElement.html(Mustache.render(fileTemplate, {
                previewImage: defaultPreview,
                icon: 'fa-file-o'
            }));

            return;
        }

        filename = filename || url.substr(url.lastIndexOf("/") + 1);

        if (url.startsWith('http://') || url.startsWith('https://')) {
            $fileElement.html(Mustache.render(fileTemplate, {
                url: url,
                filename: filename,
                icon: 'fa-external-link'
            }));

            return;
        }

        const extension = url.substr(url.lastIndexOf(".") + 1).toLowerCase();

        if (extension === "jpg" || extension === "jpeg" || extension === "png") {
            $fileElement.html(Mustache.render(fileTemplate, {
                url: url,
                filename: filename,
                previewImage: url
            }));

            return;
        }

        $fileElement.html(Mustache.render(fileTemplate, {
            url: url,
            filename: filename,
            icon: determineIcon(extension)
        }));
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

    updateFile(originalUrl, originalFilename);
}
