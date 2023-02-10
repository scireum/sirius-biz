/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

let vfsModalDropzone;

function selectVFSFile(config) {

    function filter(child) {
        return config.pathRestriction === undefined || child.path.startsWith(config.pathRestriction);
    }

    function createRow(child, _modal, resolve, changeDirectory) {
        const _self = this;
        child.icon = child.directory ? "fa-folder-open" : "fa-file";

        const _fileTableRow = document.createElement("tr");
        _fileTableRow.innerHTML = Mustache.render(
            '<td><a class="file-link" href="#" data-dir="{{directory}}" data-path="{{path}}"><i class="fa {{icon}}"></i>&nbsp;{{name}}</a></td>' +
            '<td class="text-right">{{sizeString}}</td>' +
            '<td class="text-right" title="{{lastModifiedString}}">{{lastModifiedSpokenString}}</td>', child);

        _fileTableRow.querySelector(".file-link").addEventListener("click", function () {
            if (this.dataset.dir === "true") {
                changeDirectory(this.dataset.path);
            } else {
                $(_modal).modal("hide");
                resolve(this.dataset.path);
            }
        });
        return _fileTableRow;
    }

    function replaceEventHandlers(_elem, eventName, handler) {
        const _clonedElem = _elem.cloneNode(false);
        while (_elem.hasChildNodes()) {
            _clonedElem.appendChild(_elem.firstChild);
        }
        _elem.parentNode.replaceChild(_clonedElem, _elem);
        _clonedElem.addEventListener(eventName, handler);
        return _clonedElem;
    }

    return new Promise(function (resolve, reject) {
        const _modal = document.getElementById("select-file-modal");
        const _table = _modal.querySelector('.select-file-table-js');
        const _searchForm = _modal.querySelector('.search-form-js input');
        const pageSize = 25;

        document.querySelector("#select-file-modal .modal-title").textContent = config.modalTitle;

        _searchForm.value = "";

        const pagination = new Pagination(_modal.querySelector(".pagination-controls-js"), pageSize, function (page, pagination) {
            if (page <= 0) {
                pagination._previousBtn.classList.add("disabled");
            } else {
                pagination._previousBtn.classList.remove("disabled");
            }
            const url = Mustache.render("/fs/list?path={{path}}&onlyDirectories={{onlyDirectories}}&skip={{skip}}&maxItems={{maxItems}}&filter={{filter}}&extensions={{extensions}}", {
                path: encodeURIComponent(config.path),
                onlyDirectories: config.allowDirectories && !config.allowFiles,
                skip: page * pageSize,
                maxItems: pageSize + 1,
                filter: encodeURIComponent(_searchForm.value),
                extensions: encodeURIComponent(config.allowedExtensions)
            });
            fetch(url).then(function (response) {
                if (!response.ok) {
                    throw "Http response status was " + response.status;
                }
                return response.json();
            }).then(function (json) {
                _table.textContent = "";
                let numItems = Math.min(json.children.length, pageSize);
                for (let i = 0; i < numItems; i++) {
                    let child = json.children[i];
                    if (filter(child)) {
                        _table.appendChild(createRow(child, _modal, resolve, function (newPath) {
                            config.path = newPath;
                            _searchForm.value = "";
                            pagination.reset();
                        }));
                    }
                }
                pagination.setPageLabel((page * pageSize + 1) + " - " + (page * pageSize + numItems));

                _modal.querySelector(".search-result-js").style.display = _searchForm.value ? "block" : "none";

                if (json.children.length <= pageSize) {
                    pagination._nextBtn.classList.add("disabled");
                } else {
                    pagination._nextBtn.classList.remove("disabled");
                }

                const _breadcrumbs = _modal.querySelector('.breadcrumb-js');
                _breadcrumbs.textContent = '';
                for (let i = 0; i < json.path.length; i++) {
                    const element = json.path[i];
                    if (!filter(element)) {
                        continue;
                    }
                    const _folderBreadcrumb = document.createElement("li");
                    _folderBreadcrumb.innerHTML = Mustache.render('<a class="file-link" href="#" data-path={{path}}>{{{name}}}</a>', {
                        path: element.path,
                        name: element.name === "/" ? "<i class='fa fa-folder-open'></i>" : element.name
                    });
                    _folderBreadcrumb.querySelector('.file-link').addEventListener("click", function () {
                        config.path = this.dataset.path;
                        pagination.reset();
                    });
                    _breadcrumbs.appendChild(_folderBreadcrumb);
                }
                const _uploadBox = _modal.querySelector('.upload-box-js');
                if (config.allowFiles && json.canCreateChildren) {
                    _uploadBox.classList.remove('d-none');
                    _uploadBox.classList.add('dropzone');
                    if (vfsModalDropzone) {
                        vfsModalDropzone.destroy();
                    }
                    vfsModalDropzone = new Dropzone("#select-file-modal .dropzone-drop-area-js", {
                        url: function (files) {
                            return '/fs/upload?filename=' + encodeURIComponent(files[0].name) + '&path=' + config.path;
                        },
                        sendFileAsBody: true,
                        parallelUploads: 1,
                        maxFilesize: null,
                        maxFiles: 1,
                        dictMaxFilesExceeded: config.dictMaxFilesExceeded,
                        acceptedFiles: config.allowedExtensions,
                        previewTemplate: '' +
                            '<div class="dropzone-item">\n' +
                            '   <div class="dropzone-file">\n' +
                            '       <div class="dropzone-filename">\n' +
                            '           <span data-dz-name></span>\n' +
                            '           <strong>(<span data-dz-size></span>)</strong>\n' +
                            '           <span class="dz-success-mark">✔</span>\n' +
                            '           <span class="dz-error-mark">✘</span>\n' +
                            '       </div>\n' +
                            '       <div class="dropzone-error" data-dz-errormessage></div>\n' +
                            '   </div>\n' +
                            '\n' +
                            '   <div class="dropzone-progress d-flex">\n' +
                            '       <div class="progress flex-grow-1">\n' +
                            '           <div class="progress-bar bg-primary" role="progressbar" aria-valuemin="0" aria-valuemax="100" ' +
                            '                         aria-valuenow="0" data-dz-uploadprogress>' +
                            '           </div>\n' +
                            '       </div>\n' +
                            '       <span class="dropzone-delete ml-4" data-dz-remove><i class="fa fa-times"></i></span>\n' +
                            '   </div>\n' +
                            '</div>',
                        previewsContainer: '#select-file-modal .upload-box-js .dropzone-items',
                        clickable: '#select-file-modal .upload-box-js .dropzone-select',
                        error: function (file, message) {
                            if (file.status === Dropzone.CANCELED) {
                                // no need to show error to the user
                                return;
                            }
                            if (file.previewElement) {
                                file.previewElement.classList.add('dz-error');

                                if (typeof message !== 'string' && message.message) {
                                    message = message.message;
                                }

                                file.previewElement.querySelector('[data-dz-errormessage]').innerHTML = message;

                                clearMessages();
                                addErrorMessage(message);
                                setTimeout(function () {
                                    $(_modal).modal('hide');
                                    file.previewElement.remove();
                                }, 500);

                            }
                        },
                        init: function () {
                            const dropzone = this;
                            let previewsContainer = '#select-file-modal .upload-box-js #sirius-upload-progress';

                            if (previewsContainer) {
                                let _dropzoneIndicator = document.querySelector(previewsContainer + ' .sirius-upload-hover');

                                function hideIndicators() {
                                    document.querySelectorAll('.sirius-upload-hover').forEach(function (_indicator) {
                                        _indicator.classList.remove('d-flex');
                                        _indicator.classList.add('d-none');
                                        _indicator.classList.remove('sirius-upload-hover-active');
                                    });
                                }

                                document.addEventListener('dragenter', function (event) {
                                    _modal.querySelectorAll('.sirius-upload-hover').forEach(function (_indicator) {
                                        _indicator.classList.add('d-flex');
                                        _indicator.classList.remove('d-none');
                                    });
                                }, false);
                                document.addEventListener('dragover', function (event) {
                                    event.preventDefault();
                                });
                                document.addEventListener('dragend', function (event) {
                                    hideIndicators();
                                }, false);
                                document.addEventListener('drop', function (event) {
                                    hideIndicators();
                                }, false);
                                document.addEventListener('dragleave', function (event) {
                                    if (sirius.isDragleaveEventLeavingWindow(event)) {
                                        hideIndicators();
                                    }
                                }, false);
                                _dropzoneIndicator.addEventListener('dragenter', function (event) {
                                    _dropzoneIndicator.classList.add('sirius-upload-hover-active');
                                });
                                _dropzoneIndicator.addEventListener('dragleave', function (event) {
                                    _dropzoneIndicator.classList.remove('sirius-upload-hover-active');
                                });
                                _dropzoneIndicator.addEventListener('dragover', function (event) {
                                    event.preventDefault();
                                });
                                _dropzoneIndicator.addEventListener('drop', function (event) {
                                    event.preventDefault();
                                });
                                dropzone.on('drop', function () {
                                    hideIndicators();
                                });
                            }
                            this.on('sending', function (file, xhr, formData) {
                                formData.append('filename', file.name);
                                formData.append('path', config.path);
                            });
                            this.on('success', function (file, response) {
                                if (file.previewElement) {
                                    setTimeout(function () {
                                        $(_modal).modal('hide');
                                        file.previewElement.remove();
                                    }, 500);
                                }
                                if (response.error) {
                                    file.previewElement.classList.add('dz-error');
                                    file.previewElement.classList.remove('dz-success');
                                    clearMessages();
                                    addErrorMessage(response.message);
                                } else {
                                    resolve(response.file);
                                }
                            });
                            this.on('maxfilesexceeded', function (file) {
                                // remove all files to reset limit, so a second separate upload is possible
                                this.removeAllFiles();
                                this.addFile(file);
                            });
                        }
                    });
                } else {
                    _uploadBox.classList.add('d-none');
                    _uploadBox.classList.remove('dropzone');
                }
            });

        });

        replaceEventHandlers(_modal.querySelector('.search-form-js .search-btn-js'), 'click', function () {
            pagination.reset();
        });
        replaceEventHandlers(_modal.querySelector('.search-form-js'), 'submit', function (event) {
            event.preventDefault();
            pagination.reset();
        });
        replaceEventHandlers(_modal.querySelector('.ok-btn-js'), 'click', function () {
            $(_modal).modal('hide');
            resolve(config.path);
        });

        _modal.querySelector('.ok-btn-js').parentElement.style.display = config.allowDirectories ? 'inline-block' : 'none';

        $(_modal).modal('show');
    });
}

function createInplaceDropzone(basePath, localId, _input, allowedExtensions, dictMaxFilesExceeded) {
    new Dropzone("#sirius-upload-progress-" + localId, {
        url: function (files) {
            let value = _input.value;

            if (value == null || value === '') {
                value = basePath;
            } else {
                value = value.substr(0, value.lastIndexOf("/"))
            }
            return '/fs/upload?filename=' + encodeURIComponent(files[0].name) + '&path=' + value;
        },
        sendFileAsBody: true,
        parallelUploads: 1,
        maxFilesize: null,
        // even though we only want 1 file, we allow multiple
        // this avoids user errors when an existing upload should be replaced
        maxFiles: null,
        dictMaxFilesExceeded: dictMaxFilesExceeded,
        acceptedFiles: allowedExtensions,
        previewTemplate: '' +
            '<div class="dropzone-item">\n' +
            '   <div class="dropzone-file">\n' +
            '       <div class="dropzone-filename">\n' +
            '           <span data-dz-name></span>\n' +
            '           <strong>(<span data-dz-size></span>)</strong>\n' +
            '           <span class="dz-success-mark">✔</span>\n' +
            '           <span class="dz-error-mark">✘</span>\n' +
            '       </div>\n' +
            '       <div class="dropzone-error" data-dz-errormessage></div>\n' +
            '   </div>\n' +
            '\n' +
            '   <div class="dropzone-progress d-flex">\n' +
            '       <div class="progress flex-grow-1">\n' +
            '           <div class="progress-bar bg-primary" role="progressbar" aria-valuemin="0" aria-valuemax="100" ' +
            '                         aria-valuenow="0" data-dz-uploadprogress>' +
            '           </div>\n' +
            '       </div>\n' +
            '       <span class="dropzone-delete ml-4" data-dz-remove><i class="fa fa-times"></i></span>\n' +
            '   </div>\n' +
            '</div>',
        previewsContainer: '#sirius-upload-progress-' + localId,
        clickable: '#sirius-upload-progress-' + localId,
        error: function (file, message) {
            if (file.status === Dropzone.CANCELED) {
                // no need to show error to the user
                return;
            }
            if (file.previewElement) {
                file.previewElement.classList.add('dz-error');

                if (typeof message !== 'string' && message.message) {
                    message = message.message;
                }

                file.previewElement.querySelector('[data-dz-errormessage]').innerHTML = message;

                clearMessages();
                addErrorMessage(message);
                setTimeout(function () {
                    file.previewElement.remove();
                }, 500);

            }
        },
        init: function () {
            const dropzone = this;
            let previewsContainer = '#sirius-upload-progress-' + localId;
            let _outerContainer = document.querySelector(previewsContainer);

            if (previewsContainer) {
                let _dropzoneIndicator = document.querySelector(previewsContainer + ' .sirius-upload-hover');

                function hideIndicators() {
                    document.querySelectorAll(previewsContainer + ' .sirius-upload-hover').forEach(function (_indicator) {
                        if (dropzone.getQueuedFiles().length + dropzone.getUploadingFiles().length === 0) {
                            setTimeout(function () {
                                _indicator.parentElement.classList.add('d-none');
                            }, 500);
                        }
                        _indicator.classList.remove('d-flex');
                        _indicator.classList.add('d-none');
                        _indicator.classList.remove('sirius-upload-hover-active');
                    });
                }

                document.addEventListener('dragenter', function (event) {
                    let _modal = document.querySelector('#select-file-modal');
                    if (window.getComputedStyle(_modal).display === 'none') {
                        _outerContainer.querySelectorAll('.sirius-upload-hover').forEach(function (_indicator) {
                            _outerContainer.classList.remove('d-none');
                            _indicator.classList.add('d-flex');
                            _indicator.classList.remove('d-none');
                        });
                    }
                }, false);
                document.addEventListener('dragover', function (event) {
                    event.preventDefault();
                });
                document.addEventListener('dragleave', function (event) {
                    if (sirius.isDragleaveEventLeavingWindow(event)) {
                        hideIndicators();
                    }
                }, false);
                _dropzoneIndicator.addEventListener('dragenter', function (event) {
                    _dropzoneIndicator.classList.add('sirius-upload-hover-active');
                });
                _dropzoneIndicator.addEventListener('dragleave', function (event) {
                    _dropzoneIndicator.classList.remove('sirius-upload-hover-active');
                });
                _dropzoneIndicator.addEventListener('dragover', function (event) {
                    event.preventDefault();
                });
                _dropzoneIndicator.addEventListener('drop', function (event) {
                    event.preventDefault();
                });
                dropzone.on('sending', function () {
                    hideIndicators();
                });
                dropzone.on('reset', function () {
                    hideIndicators();
                });
            }
            this.on('sending', function (file, xhr, formData) {
                let value = _input.value;

                if (value == null || value === '') {
                    value = basePath;
                } else {
                    value = value.substr(0, value.lastIndexOf("/"))
                }
                formData.append('filename', file.name);
                formData.append('path', value);
            });
            this.on('success', function (file, response) {
                if (file.previewElement) {
                    setTimeout(function () {
                        file.previewElement.remove();
                        _outerContainer.classList.add('d-none');
                    }, 500);
                }
                if (response.error) {
                    file.previewElement.classList.add('dz-error');
                    file.previewElement.classList.remove('dz-success');
                    clearMessages();
                    addErrorMessage(response.message);
                } else {
                    _input.value = response.file;
                    sirius.dispatchEvent("change", _input);
                }
            });
            this.on('maxfilesexceeded', function (file) {
                // remove all files to reset limit, so a second separate upload is possible
                this.removeAllFiles();
                this.addFile(file);
            });
        }
    });
}
