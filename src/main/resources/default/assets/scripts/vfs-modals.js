/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

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
            '<td class="text-right">{{lastModifiedString}}</td>', child);

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
            const url = Mustache.render("/fs/list?path={{path}}&onlyDirectories={{onlyDirectories}}&skip={{skip}}&maxItems={{maxItems}}&filter={{filter}}", {
                path: encodeURIComponent(config.path),
                onlyDirectories: config.allowDirectories && !config.allowFiles,
                skip: page * pageSize,
                maxItems: pageSize + 1,
                filter: encodeURIComponent(_searchForm.value)
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
                if (config.allowUpload && json.canCreateChildren) {
                    if (!_uploadBox.classList.contains('dropzone')) {
                        _uploadBox.classList.remove('d-none');
                        _uploadBox.classList.add('dropzone');
                        new Dropzone("#select-file-modal .upload-box-js", {
                            url: function (files) {
                                return '/fs/upload?filename=' + files[0].name + '&path=' + config.path;
                            },
                            sendFileAsBody: true,
                            parallelUploads: 1,
                            maxFilesize: null,
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
                            init: function () {
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
                                        clearMessages();
                                        addErrorMessage(response.message);
                                    } else {
                                        resolve(response.file);
                                    }
                                });
                                this.on('error', function (file, response) {
                                    if (file.previewElement) {
                                        setTimeout(function () {
                                            $(_modal).modal('hide');
                                            file.previewElement.remove();
                                        }, 500);
                                    }
                                    clearMessages();
                                    addErrorMessage(response.message);
                                });
                            }
                        })
                    }
                } else {
                    _uploadBox.classList.add('d-none');
                    _uploadBox.classList.remove('dropzone');
                }
            });

        });

        replaceEventHandlers(_modal.querySelector('.search-form-js .search-btn-js'), "click", function () {
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

        _modal.querySelector(".ok-btn-js").parentElement.style.display = config.allowDirectories ? "inline-block" : "none";

        $(_modal).modal('show');
    });
}
